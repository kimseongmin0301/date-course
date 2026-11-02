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
import com.datecourse.domain.place.service.FranchiseNames;

/**
 * 코스 검색·상세 SQL (JdbcClient). 검색 순위 = course.score(고정 부분) + 키워드 일치 + 가까움.
 *   키워드 일치  40 × (고른 키워드 중 코스에 있는 수 ÷ 고른 수)
 *   가까움       10 × (1 − 거리 ÷ 반경)
 *   메뉴         30 (메뉴를 골랐고 그 메뉴 식당·카페가 든 코스, Foods)
 *   가게        100 (가게 이름으로 찾았고 그 가게가 든 코스: 맨 위로)
 * 거리는 코스 가운데까지 직선(위경도를 미터로 펴서 계산, 반경 수 km 라 충분히 정확).
 */
@Repository
public class CourseQueryRepository {

    public static final double KEYWORD_WEIGHT = 40;
    public static final double DISTANCE_WEIGHT = 10;
    public static final double FOOD_WEIGHT = 30;
    public static final double SHOP_WEIGHT = 100;

    private final JdbcClient jdbc;
    private final FranchiseNames franchises;

    public CourseQueryRepository(JdbcClient jdbc, FranchiseNames franchises) {
        this.jdbc = jdbc;
        this.franchises = franchises;
    }

    /**
     * foodMatch: 고른 메뉴의 가게가 든 코스인지, foodTotal: 그런 코스가 범위 안에 모두 몇 개인지 (메뉴를 안 골랐으면 false·0).
     * shopMatch·shopTotal: 가게 이름으로 찾은 그 가게가 든 코스 (가게가 없으면 false·0)
     */
    public record Row(long id, String title, String summary, String kind, String sido, String sigungu,
                      double centerLat, double centerLng, Integer totalMinutes, double distance, int total, boolean foodMatch, int foodTotal,
                      boolean shopMatch, int shopTotal) {

        public Row(long id, String title, String summary, String kind, String sido, String sigungu,
                   double centerLat, double centerLng, Integer totalMinutes, double distance, int total) {
            this(id, title, summary, kind, sido, sigungu, centerLat, centerLng, totalMinutes, distance, total, false, 0, false, 0);
        }
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

    /**
     * 풍경(바다·산·물가) 조건: 관광명소·문화시설·카페 단계의 이름이나 업종이 이 정규식에 맞는 코스만. null 이면 조건 없음.
     * 식당·소품샵 이름은 풍경과 상관없어서 보지 않는다 (익산의 횟집 "동해안"이 바다 코스로 잡힌 적이 있음)
     */
    private static final String SCENE_SQL = " AND (CAST(:scene AS varchar) IS NULL OR EXISTS (SELECT 1 FROM course_step s JOIN place p ON p.id = s.place_id"
            + " WHERE s.course_id = c.id AND p.category IN ('attraction', 'culture', 'cafe')"
            + " AND (p.name ~ CAST(:scene AS varchar) OR coalesce(p.detail_category, '') ~ CAST(:scene AS varchar))))";

    /**
     * 메뉴: 식당·카페·술집 단계의 이름이나 업종이 정규식에 맞으면 hit. 거르지 않고 순위만 올린다. 메뉴가 없으면(null) 늘 false
     */
    private static final String FOOD_JOIN = """
                CROSS JOIN LATERAL (SELECT CAST(:food AS varchar) IS NOT NULL AND EXISTS (SELECT 1 FROM course_step fs JOIN place fp ON fp.id = fs.place_id
                                    WHERE fs.course_id = c.id AND fp.category IN ('restaurant', 'cafe', 'bar')
                                      AND (fp.name ~ CAST(:food AS varchar) OR coalesce(fp.detail_category, '') ~ CAST(:food AS varchar))) AS hit) f
                CROSS JOIN LATERAL (SELECT CAST(:shop AS bigint) IS NOT NULL AND EXISTS (SELECT 1 FROM course_step ss
                                    WHERE ss.course_id = c.id AND ss.place_id = CAST(:shop AS bigint)) AS hit) sh
            """;

    /** 반경 안의 숨기지 않은 코스를 순위대로 (total: 반경 안 전체 수) */
    public List<Row> search(double lat, double lng, int radius, List<String> keywords, int limit, int offset) {
        return search(lat, lng, radius, keywords, null, null, null, limit, offset);
    }

    /** sceneRegex: 풍경 조건, foodRegex: 메뉴, shopId: 가게 (모두 null 이면 없음) */
    public List<Row> search(double lat, double lng, int radius, List<String> keywords, String sceneRegex, String foodRegex, Long shopId,
                            int limit, int offset) {
        // 키워드를 안 고르면 IN 이 비지 않게 없는 code 를 넣는다 (일치 점수는 0)
        List<String> codes = keywords.isEmpty() ? List.of("-") : keywords;
        return jdbc.sql("""
                SELECT c.id, c.title, c.summary, c.kind, c.sido, c.sigungu, c.center_lat, c.center_lng, c.total_minutes,
                       d.dist, count(*) OVER () AS total, f.hit, count(*) FILTER (WHERE f.hit) OVER () AS food_total,
                       sh.hit AS shop_hit, count(*) FILTER (WHERE sh.hit) OVER () AS shop_total
                FROM course c
                CROSS JOIN LATERAL (SELECT sqrt(power((c.center_lat - :lat) * 111320, 2)
                                              + power((c.center_lng - :lng) * 111320 * cos(radians(:lat)), 2)) AS dist) d
            """ + FOOD_JOIN + """
                LEFT JOIN (SELECT ck.course_id, count(*) AS matched
                           FROM course_keyword ck JOIN keyword k ON k.id = ck.keyword_id
                           WHERE k.code IN (:codes) GROUP BY ck.course_id) m ON m.course_id = c.id
                WHERE NOT c.hidden
                  AND c.center_lat BETWEEN :latMin AND :latMax AND c.center_lng BETWEEN :lngMin AND :lngMax
                  AND d.dist <= :radius""" + SCENE_SQL + """

                ORDER BY c.score + :kwWeight * COALESCE(m.matched, 0) / :selected + :distWeight * (1 - d.dist / :radius)
                         + CASE WHEN f.hit THEN :foodWeight ELSE 0 END + CASE WHEN sh.hit THEN :shopWeight ELSE 0 END DESC, c.id
                LIMIT :limit OFFSET :offset""")
                .param("scene", sceneRegex).param("food", foodRegex).param("foodWeight", FOOD_WEIGHT)
                .param("shop", shopId).param("shopWeight", SHOP_WEIGHT)
                .param("lat", lat).param("lng", lng).param("radius", (double) radius)
                .param("latMin", lat - Geo.latDelta(radius)).param("latMax", lat + Geo.latDelta(radius))
                .param("lngMin", lng - Geo.lngDelta(radius, lat)).param("lngMax", lng + Geo.lngDelta(radius, lat))
                .param("codes", codes).param("selected", (double) Math.max(1, keywords.size()))
                .param("kwWeight", KEYWORD_WEIGHT).param("distWeight", DISTANCE_WEIGHT)
                .param("limit", limit).param("offset", offset)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getString("title"), rs.getString("summary"), rs.getString("kind"),
                        rs.getString("sido"), rs.getString("sigungu"), rs.getDouble("center_lat"), rs.getDouble("center_lng"),
                        (Integer) rs.getObject("total_minutes"), rs.getDouble("dist"), rs.getInt("total"),
                        rs.getBoolean("hit"), rs.getInt("food_total"), rs.getBoolean("shop_hit"), rs.getInt("shop_total")))
                .list();
    }

    /**
     * 시도 전체나 전국에서 순위대로 (AI 검색: "부산 바닷가", "바다 가고 싶어"). 거리는 없으므로 코스 점수 + 키워드 일치로만.
     * sidos(비면 전국)와 sceneRegex 중 하나는 있어야 한다 (둘 다 없으면 전국 전체라 의미가 없음). "전라도"·"서해"처럼 시도 여럿도 된다
     */
    public List<Row> searchRegion(List<String> sidos, List<String> keywords, String sceneRegex, String foodRegex, int limit, int offset) {
        List<String> codes = keywords.isEmpty() ? List.of("-") : keywords;
        return jdbc.sql("""
                SELECT c.id, c.title, c.summary, c.kind, c.sido, c.sigungu, c.center_lat, c.center_lng, c.total_minutes,
                       0.0 AS dist, count(*) OVER () AS total, f.hit, count(*) FILTER (WHERE f.hit) OVER () AS food_total
                FROM course c
            """ + FOOD_JOIN + """
                LEFT JOIN (SELECT ck.course_id, count(*) AS matched
                           FROM course_keyword ck JOIN keyword k ON k.id = ck.keyword_id
                           WHERE k.code IN (:codes) GROUP BY ck.course_id) m ON m.course_id = c.id
                WHERE NOT c.hidden AND (:allSido OR c.sido IN (:sidos))""" + SCENE_SQL + """

                ORDER BY c.score + :kwWeight * COALESCE(m.matched, 0) / :selected + CASE WHEN f.hit THEN :foodWeight ELSE 0 END DESC, c.id
                LIMIT :limit OFFSET :offset""")
                .param("shop", (Long) null)
                .param("scene", sceneRegex).param("food", foodRegex).param("foodWeight", FOOD_WEIGHT).param("allSido", sidos.isEmpty()).param("sidos", sidos.isEmpty() ? List.of("-") : sidos)
                .param("codes", codes).param("selected", (double) Math.max(1, keywords.size()))
                .param("kwWeight", KEYWORD_WEIGHT)
                .param("limit", limit).param("offset", offset)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getString("title"), rs.getString("summary"), rs.getString("kind"),
                        rs.getString("sido"), rs.getString("sigungu"), rs.getDouble("center_lat"), rs.getDouble("center_lng"),
                        (Integer) rs.getObject("total_minutes"), 0, rs.getInt("total"), rs.getBoolean("hit"), rs.getInt("food_total"),
                        false, 0))
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
                       EXISTS (SELECT 1 FROM place_badge b WHERE b.place_id = p.id) AS badge, p.normalized_name
                FROM course_step cs JOIN place p ON p.id = cs.place_id
                WHERE cs.course_id IN (:ids)
                ORDER BY cs.course_id, cs.step_order""")
                .param("ids", courseIds)
                .query((rs, n) -> new StepPlace(rs.getLong("course_id"), rs.getInt("step_order"), rs.getString("role"),
                        (Integer) rs.getObject("stay_minutes"), (Integer) rs.getObject("walk_minutes"), rs.getString("memo"),
                        rs.getLong("place_id"), rs.getString("name"), rs.getString("branch"), rs.getString("category"),
                        rs.getString("detail_category"), rs.getDouble("lat"), rs.getDouble("lng"), rs.getString("sido"),
                        rs.getString("sigungu"), rs.getString("road_address"), rs.getString("lot_address"), rs.getString("phone"),
                        rs.getString("image_url"), rs.getString("description"),
                        rs.getBoolean("tour"), rs.getBoolean("sbiz"), rs.getBoolean("badge"), franchises.isFranchise(rs.getString("normalized_name"))))
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

    /** id 로 장소들 (저장한 일정을 다시 보여줄 때). 문 닫은 곳도 돌려준다 (화면에서 알리게) */
    public List<Candidate> placesByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT p.id, p.name, p.branch, p.normalized_name, p.category, p.detail_category, p.lat, p.lng, p.sido, p.sigungu,
                       p.road_address, p.lot_address, p.phone, p.image_url, p.description, NULL AS codes,
                       EXISTS (SELECT 1 FROM place_source s WHERE s.place_id = p.id AND s.source = 'tour') AS tour,
                       EXISTS (SELECT 1 FROM place_source s WHERE s.place_id = p.id AND s.source = 'sbiz') AS sbiz,
                       EXISTS (SELECT 1 FROM place_badge b WHERE b.place_id = p.id) AS badge
                FROM place p WHERE p.id IN (:ids)""")
                .param("ids", ids)
                .query((rs, n) -> new Candidate(rs.getLong("id"), rs.getString("name"), rs.getString("branch"), rs.getString("normalized_name"),
                        rs.getString("category"), rs.getString("detail_category"), rs.getDouble("lat"), rs.getDouble("lng"),
                        rs.getString("sido"), rs.getString("sigungu"), rs.getString("road_address"), rs.getString("lot_address"),
                        rs.getString("phone"), rs.getString("image_url"), rs.getString("description"), Set.of(),
                        rs.getBoolean("tour"), rs.getBoolean("sbiz"), rs.getBoolean("badge"),
                        franchises.isFranchise(rs.getString("normalized_name"))))
                .list();
    }

    /** (lat, lng) 에서 meters 안의 categories 장소 (거리 계산 전 사각형으로 거름) */
    public List<Candidate> candidates(double lat, double lng, double meters, List<String> categories) {
        return candidates(lat, lng, meters, categories, null);
    }

    /**
     * words: 말마다 정리한 이름이나 업종 이름에 든 곳만 (null 이면 모두).
     * "카페 높" → 이름·업종에 "카페"도 "높"도 든 곳 (업종 "카페"인 가게 "높")
     */
    public List<Candidate> candidates(double lat, double lng, double meters, List<String> categories, List<String> words) {
        List<String> terms = words == null ? List.of() : words;
        StringBuilder wordSql = new StringBuilder();
        for (int i = 0; i < terms.size(); i++) {
            wordSql.append(" AND (p.normalized_name LIKE :w").append(i).append(" OR replace(p.detail_category, ' ', '') LIKE :w").append(i).append(')');
        }
        var spec = jdbc.sql("""
                SELECT p.id, p.name, p.branch, p.normalized_name, p.category, p.detail_category, p.lat, p.lng, p.sido, p.sigungu,
                       p.road_address, p.lot_address, p.phone, p.image_url, p.description,
                       (SELECT string_agg(k.code, ',') FROM place_keyword pk JOIN keyword k ON k.id = pk.keyword_id
                        WHERE pk.place_id = p.id) AS codes,
                       EXISTS (SELECT 1 FROM place_source s WHERE s.place_id = p.id AND s.source = 'tour') AS tour,
                       EXISTS (SELECT 1 FROM place_source s WHERE s.place_id = p.id AND s.source = 'sbiz') AS sbiz,
                       EXISTS (SELECT 1 FROM place_badge b WHERE b.place_id = p.id) AS badge
                FROM place p
                WHERE p.closed_at IS NULL AND p.category IN (:categories)
                  AND p.lat BETWEEN :latMin AND :latMax AND p.lng BETWEEN :lngMin AND :lngMax""" + wordSql)
                .param("categories", categories);
        for (int i = 0; i < terms.size(); i++) {
            spec = spec.param("w" + i, "%" + terms.get(i) + "%");
        }
        return spec
                .param("latMin", lat - Geo.latDelta(meters)).param("latMax", lat + Geo.latDelta(meters))
                .param("lngMin", lng - Geo.lngDelta(meters, lat)).param("lngMax", lng + Geo.lngDelta(meters, lat))
                .query((rs, n) -> {
                    String codes = rs.getString("codes");
                    return new Candidate(rs.getLong("id"), rs.getString("name"), rs.getString("branch"), rs.getString("normalized_name"),
                            rs.getString("category"), rs.getString("detail_category"), rs.getDouble("lat"), rs.getDouble("lng"),
                            rs.getString("sido"), rs.getString("sigungu"), rs.getString("road_address"), rs.getString("lot_address"),
                            rs.getString("phone"), rs.getString("image_url"), rs.getString("description"),
                            codes == null ? Set.of() : Set.of(codes.split(",")),
                            rs.getBoolean("tour"), rs.getBoolean("sbiz"), rs.getBoolean("badge"),
                            franchises.isFranchise(rs.getString("normalized_name")));
                })
                .list();
    }
}
