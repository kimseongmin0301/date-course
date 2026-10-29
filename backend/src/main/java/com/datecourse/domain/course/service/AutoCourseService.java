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

import com.datecourse.domain.collector.util.Geo;
import com.datecourse.domain.course.util.CoursePlanner;
import com.datecourse.domain.course.util.PlaceGrid;
import com.datecourse.domain.place.entity.PlaceCategory;

/**
 * 자동 코스를 전국에서 다시 만든다 (CoursePlanner). 시도마다 장소를 메모리에 올려 조합하고, 시도 하나를 한 트랜잭션으로 쓴다.
 * 중심 장소마다 자동 코스는 하나라서, 이미 있으면 같은 id 로 고쳐 쓴다 (반응·저장이 이어지게).
 * 이번에 못 만든 예전 자동 코스는 반응이 없으면 지우고, 있으면 숨긴다.
 * 시도 경계 너머의 카페·식당은 보지 않는다 (경계 바로 옆 명소는 후보가 조금 적다).
 *
 * 목적지 카페 코스: 관광공사가 소개하고 체인이 아닌 카페 중 명소 코스에 안 들어간 곳은 그 카페를 중심으로
 * (근처 구경거리) → 카페 → 식당 코스를 만든다 (갤러리 카페·바다 앞 카페 같은 "구석구석" 좋은 곳이 빠지지 않게).
 *
 * 동네 코스: 명소 코스가 1.5km 안에 하나도 없는 동네(주거지·역세권)는 소품샵·서점·놀거리를 중심으로
 * 같은 규칙(구경 → 카페 → 식당)으로 만든다. 약 1km 칸마다 가장 나은 구경거리 하나를 중심으로 해 본다.
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
        }
        int[] stale = tx.execute(status -> removeStale(made));
        log.info("자동 코스 끝: {}개, 예전 코스 지움 {}·숨김 {} ({}초)", made.size(), stale[0], stale[1],
                (System.currentTimeMillis() - started) / 1000);
        return made.size();
    }

    private List<CoursePlanner.Plan> planSido(String sido, Set<String> franchises) {
        PlaceGrid grid = new PlaceGrid();
        List<CoursePlanner.Spot> anchors = new ArrayList<>();
        List<CoursePlanner.Spot> neighborhood = new ArrayList<>();
        List<CoursePlanner.Spot> cafes = new ArrayList<>();
        jdbc.query("""
                SELECT p.id, p.name, p.normalized_name, p.category, p.detail_category, p.lat, p.lng, p.sido, p.sigungu,
                       (SELECT string_agg(k.code, ',') FROM place_keyword pk JOIN keyword k ON k.id = pk.keyword_id
                        WHERE pk.place_id = p.id) AS codes,
                       EXISTS (SELECT 1 FROM place_source s WHERE s.place_id = p.id AND s.source = 'tour') AS tour,
                       EXISTS (SELECT 1 FROM place_badge b WHERE b.place_id = p.id) AS badge
                FROM place p
                WHERE p.sido = ? AND p.closed_at IS NULL AND p.category <> 'bar'""", rs -> {
            String codes = rs.getString("codes");
            String normalized = rs.getString("normalized_name");
            CoursePlanner.Spot spot = new CoursePlanner.Spot(rs.getLong("id"), rs.getString("name"), normalized,
                    PlaceCategory.valueOf(rs.getString("category").toUpperCase()), rs.getString("detail_category"),
                    rs.getDouble("lat"), rs.getDouble("lng"), rs.getString("sido"), rs.getString("sigungu"),
                    codes == null ? Set.of() : Set.of(codes.split(",")), rs.getBoolean("tour"), franchises.contains(normalized),
                    rs.getBoolean("badge"));
            grid.add(spot);
            if (CoursePlanner.isAnchor(spot)) {
                anchors.add(spot);
            } else if (CoursePlanner.isNeighborhoodAnchor(spot)) {
                neighborhood.add(spot);
            } else if (CoursePlanner.isDestinationCafe(spot)) {
                cafes.add(spot);
            }
        }, sido);
        List<CoursePlanner.Plan> plans = new ArrayList<>();
        Map<Long, Integer> used = new HashMap<>();
        for (CoursePlanner.Spot anchor : anchors) {
            CoursePlanner.plan(anchor, grid, used).ifPresent(plans::add);
        }
        int landmark = plans.size();
        for (CoursePlanner.Spot cafe : cafes) {
            // 명소 코스에 이미 들어간 카페는 그 코스로 충분하다
            if (!used.containsKey(cafe.id())) {
                CoursePlanner.planAroundCafe(cafe, grid, used).ifPresent(plans::add);
            }
        }
        int cafeCourses = plans.size() - landmark;
        plans.addAll(planNeighborhoods(neighborhood, plans, grid, used));
        log.info("자동 코스: {} 명소 {}개 + 카페 {}개 + 동네 {}개", sido, landmark, cafeCourses, plans.size() - landmark - cafeCourses);
        return plans;
    }

    /** 명소 코스가 없는 동네를 칸(약 1km)마다 채운다. 칸 안에서 가장 나은 구경거리로, 이미 1.5km 안에 코스가 있으면 건너뛴다 */
    private static List<CoursePlanner.Plan> planNeighborhoods(List<CoursePlanner.Spot> candidates, List<CoursePlanner.Plan> existing,
                                                             PlaceGrid grid, Map<Long, Integer> used) {
        Map<Long, CoursePlanner.Spot> bestPerCell = new java.util.TreeMap<>();
        for (CoursePlanner.Spot s : candidates) {
            long cell = PlaceGrid.cellKey(s.lat(), s.lng());
            bestPerCell.merge(cell, s, (a, b) -> {
                int c = Double.compare(CoursePlanner.anchorRank(a), CoursePlanner.anchorRank(b));
                return c > 0 || (c == 0 && a.id() < b.id()) ? a : b;
            });
        }
        List<double[]> centers = new ArrayList<>();
        existing.forEach(p -> centers.add(new double[] {p.centerLat(), p.centerLng()}));
        List<CoursePlanner.Plan> made = new ArrayList<>();
        for (CoursePlanner.Spot anchor : bestPerCell.values()) {
            boolean covered = centers.stream().anyMatch(c ->
                    Math.abs(c[0] - anchor.lat()) < 0.02
                            && Geo.distanceMeters(c[0], c[1], anchor.lat(), anchor.lng()) < CoursePlanner.NEIGHBORHOOD_GAP_M);
            if (covered) {
                continue;
            }
            CoursePlanner.plan(anchor, grid, used).ifPresent(p -> {
                made.add(p);
                centers.add(new double[] {p.centerLat(), p.centerLng()});
            });
        }
        return made;
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
