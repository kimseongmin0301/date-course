package com.datecourse.domain.course.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.datecourse.domain.collector.util.Geo;
import com.datecourse.domain.course.dto.StepSummary;
import com.datecourse.domain.course.util.CoursePlanner;

/**
 * 코스 검색·상세 SQL (JdbcClient). 검색 순위 = course.score(고정 부분) + 키워드 일치 + 가까움.
 *   키워드 일치  40 × (고른 키워드 중 코스에 있는 수 ÷ 고른 수)
 *   가까움       10 × (1 − 거리 ÷ 반경)
 * 거리는 코스 가운데까지 직선(위경도를 미터로 펴서 계산, 반경 수 km 라 충분히 정확).
 */
@Repository
public class CourseQueryRepository {

    public static final double KEYWORD_WEIGHT = 40;
    public static final double DISTANCE_WEIGHT = 10;

    private final JdbcClient jdbc;

    public CourseQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Row(long id, String title, String summary, String kind, String sido, String sigungu,
                      double centerLat, double centerLng, Integer totalMinutes, double distance, int total) {
    }

    public record Detail(long id, String title, String summary, String kind, String sido, String sigungu,
                         double centerLat, double centerLng, Integer totalMinutes, Integer budgetMin, Integer budgetMax) {
    }

    public record StepPlace(long courseId, int order, String role, Integer stayMinutes, Integer walkMinutes, String memo,
                            long placeId, String name, String branch, String category, String detailCategory,
                            double lat, double lng, String sido, String sigungu, String roadAddress, String lotAddress,
                            String phone, String imageUrl, String description,
                            boolean tour, boolean sbiz, boolean badge, boolean franchise) {

        public StepSummary summary() {
            return new StepSummary(order, role, stayMinutes, walkMinutes, placeId, name, branch, category, lat, lng);
        }
    }

    /** 반경 안의 숨기지 않은 코스를 순위대로 (total: 반경 안 전체 수) */
    public List<Row> search(double lat, double lng, int radius, List<String> keywords, int limit, int offset) {
        // 키워드를 안 고르면 IN 이 비지 않게 없는 code 를 넣는다 (일치 점수는 0)
        List<String> codes = keywords.isEmpty() ? List.of("-") : keywords;
        return jdbc.sql("""
                SELECT c.id, c.title, c.summary, c.kind, c.sido, c.sigungu, c.center_lat, c.center_lng, c.total_minutes,
                       d.dist, count(*) OVER () AS total
                FROM course c
                CROSS JOIN LATERAL (SELECT sqrt(power((c.center_lat - :lat) * 111320, 2)
                                              + power((c.center_lng - :lng) * 111320 * cos(radians(:lat)), 2)) AS dist) d
                LEFT JOIN (SELECT ck.course_id, count(*) AS matched
                           FROM course_keyword ck JOIN keyword k ON k.id = ck.keyword_id
                           WHERE k.code IN (:codes) GROUP BY ck.course_id) m ON m.course_id = c.id
                WHERE NOT c.hidden
                  AND c.center_lat BETWEEN :latMin AND :latMax AND c.center_lng BETWEEN :lngMin AND :lngMax
                  AND d.dist <= :radius
                ORDER BY c.score + :kwWeight * COALESCE(m.matched, 0) / :selected + :distWeight * (1 - d.dist / :radius) DESC, c.id
                LIMIT :limit OFFSET :offset""")
                .param("lat", lat).param("lng", lng).param("radius", (double) radius)
                .param("latMin", lat - Geo.latDelta(radius)).param("latMax", lat + Geo.latDelta(radius))
                .param("lngMin", lng - Geo.lngDelta(radius, lat)).param("lngMax", lng + Geo.lngDelta(radius, lat))
                .param("codes", codes).param("selected", (double) Math.max(1, keywords.size()))
                .param("kwWeight", KEYWORD_WEIGHT).param("distWeight", DISTANCE_WEIGHT)
                .param("limit", limit).param("offset", offset)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getString("title"), rs.getString("summary"), rs.getString("kind"),
                        rs.getString("sido"), rs.getString("sigungu"), rs.getDouble("center_lat"), rs.getDouble("center_lng"),
                        (Integer) rs.getObject("total_minutes"), rs.getDouble("dist"), rs.getInt("total")))
                .list();
    }

    public Optional<Detail> detail(long id) {
        return jdbc.sql("""
                SELECT id, title, summary, kind, sido, sigungu, center_lat, center_lng, total_minutes, budget_min, budget_max
                FROM course WHERE id = :id AND NOT hidden""")
                .param("id", id)
                .query((rs, n) -> new Detail(rs.getLong("id"), rs.getString("title"), rs.getString("summary"), rs.getString("kind"),
                        rs.getString("sido"), rs.getString("sigungu"), rs.getDouble("center_lat"), rs.getDouble("center_lng"),
                        (Integer) rs.getObject("total_minutes"), (Integer) rs.getObject("budget_min"), (Integer) rs.getObject("budget_max")))
                .optional();
    }

    /** 코스마다 단계와 장소 (단계 순서대로) */
    public Map<Long, List<StepPlace>> steps(List<Long> courseIds) {
        Map<Long, List<StepPlace>> result = new LinkedHashMap<>();
        if (courseIds.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                SELECT cs.course_id, cs.step_order, cs.role, cs.stay_minutes, cs.walk_minutes, cs.memo,
                       p.id AS place_id, p.name, p.branch, p.category, p.detail_category, p.lat, p.lng, p.sido, p.sigungu,
                       p.road_address, p.lot_address, p.phone, p.image_url, p.description,
                       EXISTS (SELECT 1 FROM place_source s WHERE s.place_id = p.id AND s.source = 'tour') AS tour,
                       EXISTS (SELECT 1 FROM place_source s WHERE s.place_id = p.id AND s.source = 'sbiz') AS sbiz,
                       EXISTS (SELECT 1 FROM place_badge b WHERE b.place_id = p.id) AS badge,
                       (SELECT count(*) FROM place p2 WHERE p2.normalized_name = p.normalized_name AND p2.closed_at IS NULL)
                           > :franchiseMin AS franchise
                FROM course_step cs JOIN place p ON p.id = cs.place_id
                WHERE cs.course_id IN (:ids)
                ORDER BY cs.course_id, cs.step_order""")
                .param("ids", courseIds)
                .param("franchiseMin", CoursePlanner.FRANCHISE_MIN)
                .query((rs, n) -> new StepPlace(rs.getLong("course_id"), rs.getInt("step_order"), rs.getString("role"),
                        (Integer) rs.getObject("stay_minutes"), (Integer) rs.getObject("walk_minutes"), rs.getString("memo"),
                        rs.getLong("place_id"), rs.getString("name"), rs.getString("branch"), rs.getString("category"),
                        rs.getString("detail_category"), rs.getDouble("lat"), rs.getDouble("lng"), rs.getString("sido"),
                        rs.getString("sigungu"), rs.getString("road_address"), rs.getString("lot_address"), rs.getString("phone"),
                        rs.getString("image_url"), rs.getString("description"),
                        rs.getBoolean("tour"), rs.getBoolean("sbiz"), rs.getBoolean("badge"), rs.getBoolean("franchise")))
                .list()
                .forEach(s -> result.computeIfAbsent(s.courseId(), k -> new ArrayList<>()).add(s));
        return result;
    }

    /** 코스마다 키워드 code (분위기 → 활동 → 실내외 → 예산 순) */
    public Map<Long, List<String>> keywords(List<Long> courseIds) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        if (courseIds.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                SELECT ck.course_id, k.code FROM course_keyword ck JOIN keyword k ON k.id = ck.keyword_id
                WHERE ck.course_id IN (:ids)
                ORDER BY ck.course_id, array_position(ARRAY['mood', 'activity', 'setting', 'budget']::varchar[], k.kind), k.sort_order""")
                .param("ids", courseIds)
                .query((rs, n) -> Map.entry(rs.getLong("course_id"), rs.getString("code")))
                .list()
                .forEach(e -> result.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return result;
    }

    /** 근처 대안 후보 한 곳 (장소 정보 + 고르는 데 쓰는 표시) */
    public record Candidate(long id, String name, String branch, String normalizedName, String category, String detailCategory,
                            double lat, double lng, String sido, String sigungu, String roadAddress, String lotAddress,
                            String phone, String imageUrl, String description, Set<String> keywords,
                            boolean tour, boolean sbiz, boolean badge, boolean franchise) {
    }

    /** (lat, lng) 에서 meters 안의 categories 장소 (거리 계산 전 사각형으로 거름) */
    public List<Candidate> candidates(double lat, double lng, double meters, List<String> categories) {
        return jdbc.sql("""
                SELECT p.id, p.name, p.branch, p.normalized_name, p.category, p.detail_category, p.lat, p.lng, p.sido, p.sigungu,
                       p.road_address, p.lot_address, p.phone, p.image_url, p.description,
                       (SELECT string_agg(k.code, ',') FROM place_keyword pk JOIN keyword k ON k.id = pk.keyword_id
                        WHERE pk.place_id = p.id) AS codes,
                       EXISTS (SELECT 1 FROM place_source s WHERE s.place_id = p.id AND s.source = 'tour') AS tour,
                       EXISTS (SELECT 1 FROM place_source s WHERE s.place_id = p.id AND s.source = 'sbiz') AS sbiz,
                       EXISTS (SELECT 1 FROM place_badge b WHERE b.place_id = p.id) AS badge,
                       (SELECT count(*) FROM place p2 WHERE p2.normalized_name = p.normalized_name AND p2.closed_at IS NULL)
                           > :franchiseMin AS franchise
                FROM place p
                WHERE p.closed_at IS NULL AND p.category IN (:categories)
                  AND p.lat BETWEEN :latMin AND :latMax AND p.lng BETWEEN :lngMin AND :lngMax""")
                .param("categories", categories)
                .param("franchiseMin", CoursePlanner.FRANCHISE_MIN)
                .param("latMin", lat - Geo.latDelta(meters)).param("latMax", lat + Geo.latDelta(meters))
                .param("lngMin", lng - Geo.lngDelta(meters, lat)).param("lngMax", lng + Geo.lngDelta(meters, lat))
                .query((rs, n) -> {
                    String codes = rs.getString("codes");
                    return new Candidate(rs.getLong("id"), rs.getString("name"), rs.getString("branch"), rs.getString("normalized_name"),
                            rs.getString("category"), rs.getString("detail_category"), rs.getDouble("lat"), rs.getDouble("lng"),
                            rs.getString("sido"), rs.getString("sigungu"), rs.getString("road_address"), rs.getString("lot_address"),
                            rs.getString("phone"), rs.getString("image_url"), rs.getString("description"),
                            codes == null ? Set.of() : Set.of(codes.split(",")),
                            rs.getBoolean("tour"), rs.getBoolean("sbiz"), rs.getBoolean("badge"), rs.getBoolean("franchise"));
                })
                .list();
    }
}
