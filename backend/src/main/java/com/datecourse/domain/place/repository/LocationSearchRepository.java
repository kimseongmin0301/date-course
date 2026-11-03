package com.datecourse.domain.place.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.datecourse.domain.place.dto.LocationResult;

/**
 * 지역 검색 중 우리 장소 데이터로 찾는 부분 (Geocoding 없이 무료). sido 가 있으면 그 시도 안에서만.
 *  - 시군구·행정동: 그 지역 장소들의 평균 좌표 (장소가 많은 곳 먼저)
 *  - 법정동: 지번 주소의 동 이름 ("반석동"은 행정동으로는 "노은3동"이라 행정동만으로는 못 찾아서)
 *  - 역: 지하철역 좌표는 데이터에 없어서, 이름·지점명에 "반석역"이 든 가게들의 평균 좌표 (역 앞 가게들이 역 이름을 붙임)
 *  - 명소·문화시설: 정리한 이름에 검색어가 들어 있는 곳 (같은 이름 → 이름이 검색어로 시작 → 짧은 이름 순)
 * 인덱스 없이 훑지만 결과를 Redis 에 하루 두므로 같은 검색어는 한 번만 돈다.
 */
@Repository
public class LocationSearchRepository {

    /** 시도 조건 (null 이면 전국). PostgreSQL 이 null 의 타입을 알게 CAST */
    private static final String IN_SIDO = "(CAST(:sido AS varchar) IS NULL OR sido = CAST(:sido AS varchar))";

    /** 이름이 같은 가게가 이보다 많으면 체인으로 보고 가게로 찾지 않는다 */
    static final int SHOP_MAX = 3;

    private final JdbcClient jdbc;

    public LocationSearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** prefix: 끝의 시·군·구·동·역을 뗀 검색어 (성수동 → 성수: 동 이름이 성수1가1동처럼 나뉘어 있어서) */
    public List<LocationResult> areas(String prefix, String sido, int limit) {
        return jdbc.sql("""
                SELECT kind, sido, sigungu, dong, lat, lng FROM (
                    SELECT 'sigungu' AS kind, sido, sigungu, NULL AS dong, avg(lat) AS lat, avg(lng) AS lng, count(*) AS n
                    FROM place WHERE closed_at IS NULL AND sigungu LIKE :prefix AND %1$s GROUP BY sido, sigungu
                    UNION ALL
                    SELECT 'dong', sido, sigungu, dong, avg(lat), avg(lng), count(*)
                    FROM place WHERE closed_at IS NULL AND dong LIKE :prefix AND %1$s GROUP BY sido, sigungu, dong
                ) a
                ORDER BY n DESC LIMIT :limit""".formatted(IN_SIDO))
                .param("prefix", prefix + "%")
                .param("sido", sido)
                .param("limit", limit)
                .query((rs, n) -> {
                    boolean dong = "dong".equals(rs.getString("kind"));
                    String name = dong ? rs.getString("dong") : rs.getString("sigungu");
                    String detail = dong ? rs.getString("sido") + " " + rs.getString("sigungu") : rs.getString("sido");
                    return new LocationResult("area", name, detail, rs.getDouble("lat"), rs.getDouble("lng"), dong ? 1500 : 4000);
                })
                .list();
    }

    /** 법정동 (지번 주소 "… 반석동 123" 의 동). dong 은 "반석동" 처럼 끝까지 */
    public List<LocationResult> legalDongs(String dong, String sido, int limit) {
        return jdbc.sql("""
                SELECT sido, sigungu, avg(lat) AS lat, avg(lng) AS lng, count(*) AS n FROM place
                WHERE closed_at IS NULL AND lot_address LIKE :pattern AND %s
                GROUP BY sido, sigungu ORDER BY n DESC LIMIT :limit""".formatted(IN_SIDO))
                .param("pattern", "% " + dong + " %")
                .param("sido", sido)
                .param("limit", limit)
                .query((rs, n) -> new LocationResult("area", dong, rs.getString("sido") + " " + rs.getString("sigungu"),
                        rs.getDouble("lat"), rs.getDouble("lng"), 1500))
                .list();
    }

    /** 역 (station 은 "반석역"). 같은 이름의 역이 여러 도시에 있으면 시군구마다 하나씩, 가게가 2곳 이상인 곳만 */
    public List<LocationResult> stations(String station, String normalized, String sido, int limit) {
        return jdbc.sql("""
                SELECT sido, sigungu, avg(lat) AS lat, avg(lng) AS lng, count(*) AS n FROM place
                WHERE closed_at IS NULL AND (branch LIKE :branch OR normalized_name LIKE :name) AND %s
                GROUP BY sido, sigungu HAVING count(*) >= 2 ORDER BY n DESC LIMIT :limit""".formatted(IN_SIDO))
                .param("branch", station + "%")
                .param("name", "%" + normalized + "%")
                .param("sido", sido)
                .param("limit", limit)
                .query((rs, n) -> new LocationResult("station", station, rs.getString("sido") + " " + rs.getString("sigungu"),
                        rs.getDouble("lat"), rs.getDouble("lng"), 700))
                .list();
    }

    /**
     * 가게 이름 (정리한 이름이 똑같은 카페·식당·술집·소품샵·놀거리). 같은 이름이 <b>전국에</b> SHOP_MAX 곳보다 많으면 체인이거나
     * 흔한 말이라 빈 목록 ("스타벅스"는 어느 지점인지 모르고, "바다"·"카페"·"막걸리"는 그런 이름의 가게가 곳곳에 있는 그냥 낱말).
     * 시도는 센 뒤에 거른다 (시도 안에서만 세면 "부산 바다"가 부산의 가게 "바다"로 잡혔음: AI 검색 시험 모음에서 찾음)
     */
    public List<LocationResult> shops(String normalized, String sido) {
        List<String> sidoOf = new ArrayList<>();
        List<LocationResult> found = jdbc.sql("""
                SELECT id, name, branch, category, sido, sigungu, road_address, lot_address, lat, lng FROM place
                WHERE closed_at IS NULL AND category IN ('cafe', 'restaurant', 'bar', 'shop', 'activity') AND normalized_name = :exact
                ORDER BY id
                LIMIT :limit""")
                .param("exact", normalized)
                .param("limit", SHOP_MAX + 1)
                .query((rs, n) -> {
                    String address = rs.getString("road_address") != null ? rs.getString("road_address") : rs.getString("lot_address");
                    String detail = address != null ? address : rs.getString("sido") + " " + rs.getString("sigungu");
                    String name = rs.getString("branch") == null ? rs.getString("name") : rs.getString("name") + " " + rs.getString("branch");
                    sidoOf.add(rs.getString("sido"));
                    return new LocationResult("shop", name, detail, rs.getDouble("lat"), rs.getDouble("lng"), 500,
                            rs.getLong("id"), rs.getString("category"));
                })
                .list();
        if (found.size() > SHOP_MAX) {
            return List.of();
        }
        List<LocationResult> inSido = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            if (sido == null || sido.equals(sidoOf.get(i))) {
                inSido.add(found.get(i));
            }
        }
        return inSido;
    }

    /**
     * 장소 이름에 이 말이 든 곳이 limit 곳 이상인지 (흔한 말: "막걸리"·"호수"·"클래스"는 그런 이름의 가게가 몇 곳 있어도 가게 이름이라기보다 그냥 낱말).
     * 찾는 대로 멈춰서 흔한 말은 빠르고, 드문 이름도 전체를 한 번 훑는 정도(운영 약 50ms)
     */
    public boolean commonWord(String normalized, int limit) {
        Integer n = jdbc.sql("SELECT count(*) FROM (SELECT 1 FROM place WHERE normalized_name LIKE :contains LIMIT :limit) t")
                .param("contains", "%" + normalized + "%")
                .param("limit", limit)
                .query(Integer.class).single();
        return n != null && n >= limit;
    }

    /**
     * 체인 가게의 한 지점: "어니언 성수" → 이름이 "어니언"이고 지점명이나 주소에 "성수"가 든 곳 (가까운 지점이 여럿이면 지점명이 맞는 것 먼저, 하나만)
     */
    public List<LocationResult> shopBranch(String normalized, String hint, String sido) {
        return jdbc.sql("""
                SELECT id, name, branch, category, sido, sigungu, road_address, lot_address, lat, lng FROM place
                WHERE closed_at IS NULL AND category IN ('cafe', 'restaurant', 'bar', 'shop', 'activity') AND normalized_name = :exact AND %s
                  AND (coalesce(branch, '') LIKE :hint OR coalesce(road_address, '') LIKE :hint OR coalesce(lot_address, '') LIKE :hint)
                ORDER BY (coalesce(branch, '') LIKE :hint) DESC, id
                LIMIT 1""".formatted(IN_SIDO))
                .param("exact", normalized)
                .param("hint", "%" + hint + "%")
                .param("sido", sido)
                .query((rs, n) -> {
                    String address = rs.getString("road_address") != null ? rs.getString("road_address") : rs.getString("lot_address");
                    String detail = address != null ? address : rs.getString("sido") + " " + rs.getString("sigungu");
                    String name = rs.getString("branch") == null ? rs.getString("name") : rs.getString("name") + " " + rs.getString("branch");
                    return new LocationResult("shop", name, detail, rs.getDouble("lat"), rs.getDouble("lng"), 500,
                            rs.getLong("id"), rs.getString("category"));
                })
                .list();
    }

    public List<LocationResult> landmarks(String normalized, String sido, int limit) {
        return jdbc.sql("""
                SELECT name, sido, sigungu, road_address, lot_address, lat, lng FROM place
                WHERE closed_at IS NULL AND category IN ('attraction', 'culture') AND normalized_name LIKE :contains AND %s
                ORDER BY (normalized_name = :exact) DESC, (normalized_name LIKE :prefix) DESC, length(name), id
                LIMIT :limit""".formatted(IN_SIDO))
                .param("contains", "%" + normalized + "%")
                .param("exact", normalized)
                .param("prefix", normalized + "%")
                .param("sido", sido)
                .param("limit", limit)
                .query((rs, n) -> {
                    String address = rs.getString("road_address") != null ? rs.getString("road_address") : rs.getString("lot_address");
                    String detail = address != null ? address : rs.getString("sido") + " " + rs.getString("sigungu");
                    return new LocationResult("place", rs.getString("name"), detail, rs.getDouble("lat"), rs.getDouble("lng"), 500);
                })
                .list();
    }
}
