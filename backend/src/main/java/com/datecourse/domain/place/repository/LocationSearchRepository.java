package com.datecourse.domain.place.repository;

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
