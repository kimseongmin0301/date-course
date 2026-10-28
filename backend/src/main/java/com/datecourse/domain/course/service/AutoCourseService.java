package com.datecourse.domain.course.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.datecourse.domain.course.util.CoursePlanner;
import com.datecourse.domain.course.util.PlaceGrid;
import com.datecourse.domain.place.entity.PlaceCategory;

/**
 * 자동 코스를 전국에서 다시 만든다 (CoursePlanner). 시도마다 장소를 메모리에 올려 조합하고, 시도 하나를 한 트랜잭션으로 쓴다.
 * 중심 장소마다 자동 코스는 하나라서, 이미 있으면 같은 id 로 고쳐 쓴다 (반응·저장이 이어지게).
 * 이번에 못 만든 예전 자동 코스는 반응이 없으면 지우고, 있으면 숨긴다.
 * 시도 경계 너머의 카페·식당은 보지 않는다 (경계 바로 옆 명소는 후보가 조금 적다).
 */
@Service
public class AutoCourseService {

    private static final Logger log = LoggerFactory.getLogger(AutoCourseService.class);
    private static final int ID_BLOCK = 500;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public AutoCourseService(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** 만든 코스 수 */
    public int rebuildAll() {
        long started = System.currentTimeMillis();
        Map<String, Long> keywordIds = new HashMap<>();
        jdbc.query("SELECT id, code FROM keyword", rs -> {
            keywordIds.put(rs.getString("code"), rs.getLong("id"));
        });
        Set<String> franchises = new HashSet<>(jdbc.queryForList("""
                SELECT normalized_name FROM place WHERE closed_at IS NULL
                GROUP BY normalized_name HAVING count(*) > ?""", String.class, CoursePlanner.FRANCHISE_MIN));
        Map<Long, Long> existing = new HashMap<>();
        jdbc.query("SELECT anchor_place_id, id FROM course WHERE kind = 'auto' AND anchor_place_id IS NOT NULL", rs -> {
            existing.put(rs.getLong("anchor_place_id"), rs.getLong("id"));
        });
        List<String> sidos = jdbc.queryForList("SELECT DISTINCT sido FROM place ORDER BY sido", String.class);

        IdAllocator ids = new IdAllocator();
        List<Long> made = new ArrayList<>();
        for (String sido : sidos) {
            List<CoursePlanner.Plan> plans = planSido(sido, franchises);
            List<Long> courseIds = tx.execute(status -> write(plans, existing, ids, keywordIds));
            made.addAll(courseIds);
            log.info("자동 코스: {} {}개", sido, plans.size());
        }
        int[] stale = tx.execute(status -> removeStale(made));
        log.info("자동 코스 끝: {}개, 예전 코스 지움 {}·숨김 {} ({}초)", made.size(), stale[0], stale[1],
                (System.currentTimeMillis() - started) / 1000);
        return made.size();
    }

    private List<CoursePlanner.Plan> planSido(String sido, Set<String> franchises) {
        PlaceGrid grid = new PlaceGrid();
        List<CoursePlanner.Spot> anchors = new ArrayList<>();
        jdbc.query("""
                SELECT p.id, p.name, p.normalized_name, p.category, p.detail_category, p.lat, p.lng, p.sido, p.sigungu,
                       (SELECT string_agg(k.code, ',') FROM place_keyword pk JOIN keyword k ON k.id = pk.keyword_id
                        WHERE pk.place_id = p.id) AS codes,
                       EXISTS (SELECT 1 FROM place_source s WHERE s.place_id = p.id AND s.source = 'tour') AS tour
                FROM place p
                WHERE p.sido = ? AND p.closed_at IS NULL AND p.category <> 'bar'""", rs -> {
            String codes = rs.getString("codes");
            String normalized = rs.getString("normalized_name");
            CoursePlanner.Spot spot = new CoursePlanner.Spot(rs.getLong("id"), rs.getString("name"), normalized,
                    PlaceCategory.valueOf(rs.getString("category").toUpperCase()), rs.getString("detail_category"),
                    rs.getDouble("lat"), rs.getDouble("lng"), rs.getString("sido"), rs.getString("sigungu"),
                    codes == null ? Set.of() : Set.of(codes.split(",")), rs.getBoolean("tour"), franchises.contains(normalized));
            grid.add(spot);
            if (CoursePlanner.isAnchor(spot)) {
                anchors.add(spot);
            }
        }, sido);
        List<CoursePlanner.Plan> plans = new ArrayList<>();
        Map<Long, Integer> used = new HashMap<>();
        for (CoursePlanner.Spot anchor : anchors) {
            CoursePlanner.plan(anchor, grid, used).ifPresent(plans::add);
        }
        return plans;
    }

    private List<Long> write(List<CoursePlanner.Plan> plans, Map<Long, Long> existing, IdAllocator ids, Map<String, Long> keywordIds) {
        List<Object[]> courses = new ArrayList<>();
        List<Object[]> steps = new ArrayList<>();
        List<Object[]> keywords = new ArrayList<>();
        List<Long> courseIds = new ArrayList<>();
        for (CoursePlanner.Plan plan : plans) {
            long id = Optional.ofNullable(existing.get(plan.anchor().id())).orElseGet(ids::next);
            courseIds.add(id);
            courses.add(new Object[] {id, cut(plan.title(), 100), cut(plan.summary(), 300), plan.anchor().id(),
                    plan.anchor().sido(), plan.anchor().sigungu(), plan.centerLat(), plan.centerLng(), plan.totalMinutes()});
            short order = 1;
            for (CoursePlanner.Step step : plan.steps()) {
                steps.add(new Object[] {id, order++, step.place().id(), step.role().name().toLowerCase(),
                        step.stayMinutes(), step.walkMinutes()});
            }
            for (String code : plan.keywords()) {
                keywords.add(new Object[] {id, keywordIds.get(code)});
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO course (id, title, summary, kind, anchor_place_id, sido, sigungu, center_lat, center_lng, total_minutes)
                OVERRIDING SYSTEM VALUE VALUES (?, ?, ?, 'auto', ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, summary = EXCLUDED.summary, sido = EXCLUDED.sido,
                    sigungu = EXCLUDED.sigungu, center_lat = EXCLUDED.center_lat, center_lng = EXCLUDED.center_lng,
                    total_minutes = EXCLUDED.total_minutes, updated_at = now()""", courses);
        String idList = courseIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbc.update("DELETE FROM course_step WHERE course_id = ANY (string_to_array(?, ',')::bigint[])", idList);
        jdbc.update("DELETE FROM course_keyword WHERE course_id = ANY (string_to_array(?, ',')::bigint[])", idList);
        jdbc.batchUpdate("""
                INSERT INTO course_step (course_id, step_order, place_id, role, stay_minutes, walk_minutes)
                VALUES (?, ?, ?, ?, ?, ?)""", steps);
        jdbc.batchUpdate("INSERT INTO course_keyword (course_id, keyword_id) VALUES (?, ?)", keywords);
        return courseIds;
    }

    /** [지운 수, 숨긴 수] */
    private int[] removeStale(List<Long> made) {
        String idList = made.stream().map(String::valueOf).collect(Collectors.joining(","));
        int deleted = jdbc.update("""
                DELETE FROM course c WHERE c.kind = 'auto' AND c.id <> ALL (string_to_array(?, ',')::bigint[])
                  AND NOT EXISTS (SELECT 1 FROM reaction r WHERE r.course_id = c.id)""", idList);
        int hidden = jdbc.update("""
                UPDATE course SET hidden = true, updated_at = now()
                WHERE kind = 'auto' AND NOT hidden AND id <> ALL (string_to_array(?, ',')::bigint[])""", idList);
        return new int[] {deleted, hidden};
    }

    private static String cut(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }

    /** course.id 를 시퀀스에서 미리 받아 둔다 (단계·키워드가 새 코스 id 를 알아야 해서) */
    private class IdAllocator {
        private Iterator<Long> block = List.<Long>of().iterator();

        long next() {
            if (!block.hasNext()) {
                block = jdbc.queryForList("SELECT nextval(pg_get_serial_sequence('course', 'id')) FROM generate_series(1, ?)",
                        Long.class, ID_BLOCK).iterator();
            }
            return block.next();
        }
    }
}
