package com.datecourse.domain.place.repository;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.datecourse.domain.place.dto.LocationResult;

/**
 * 지역 검색 중 우리 장소 데이터로 찾는 부분 (Geocoding 없이 무료).
 *  - 시군구·동: 그 지역 장소들의 평균 좌표 (장소가 많은 곳 먼저)
 *  - 명소·문화시설: 정리한 이름에 검색어가 들어 있는 곳 (같은 이름 → 이름이 검색어로 시작 → 짧은 이름 순)
 * 인덱스 없이 훑지만 결과를 Redis 에 하루 두므로 같은 검색어는 한 번만 돈다.
 */
@Repository
public class LocationSearchRepository {

    private final JdbcClient jdbc;

    public LocationSearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** prefix: 끝의 시·군·구·동을 뗀 검색어 (성수동 → 성수: 동 이름이 성수1가1동처럼 나뉘어 있어서) */
    public List<LocationResult> areas(String prefix, int limit) {
        return jdbc.sql("""
                SELECT kind, sido, sigungu, dong, lat, lng FROM (
                    SELECT 'sigungu' AS kind, sido, sigungu, NULL AS dong, avg(lat) AS lat, avg(lng) AS lng, count(*) AS n
                    FROM place WHERE closed_at IS NULL AND sigungu LIKE :prefix GROUP BY sido, sigungu
                    UNION ALL
                    SELECT 'dong', sido, sigungu, dong, avg(lat), avg(lng), count(*)
                    FROM place WHERE closed_at IS NULL AND dong LIKE :prefix GROUP BY sido, sigungu, dong
                ) a
                ORDER BY n DESC LIMIT :limit""")
                .param("prefix", prefix + "%")
                .param("limit", limit)
                .query((rs, n) -> {
                    boolean dong = "dong".equals(rs.getString("kind"));
                    String name = dong ? rs.getString("dong") : rs.getString("sigungu");
                    String detail = dong ? rs.getString("sido") + " " + rs.getString("sigungu") : rs.getString("sido");
                    return new LocationResult("area", name, detail, rs.getDouble("lat"), rs.getDouble("lng"), dong ? 1500 : 4000);
                })
                .list();
    }

    public List<LocationResult> landmarks(String normalized, int limit) {
        return jdbc.sql("""
                SELECT name, sido, sigungu, road_address, lot_address, lat, lng FROM place
                WHERE closed_at IS NULL AND category IN ('attraction', 'culture') AND normalized_name LIKE :contains
                ORDER BY (normalized_name = :exact) DESC, (normalized_name LIKE :prefix) DESC, length(name), id
                LIMIT :limit""")
                .param("contains", "%" + normalized + "%")
                .param("exact", normalized)
                .param("prefix", normalized + "%")
                .param("limit", limit)
                .query((rs, n) -> {
                    String address = rs.getString("road_address") != null ? rs.getString("road_address") : rs.getString("lot_address");
                    String detail = address != null ? address : rs.getString("sido") + " " + rs.getString("sigungu");
                    return new LocationResult("place", rs.getString("name"), detail, rs.getDouble("lat"), rs.getDouble("lng"), 1500);
                })
                .list();
    }
}
