package com.datecourse.domain.suggestion.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.datecourse.domain.collector.util.Geo;

/** 이용자가 알려 준 가게 (place_suggestion, V5). 엔티티 없이 JdbcClient 로만 쓴다 */
@Repository
public class SuggestionRepository {

    private final JdbcClient jdbc;

    public SuggestionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Row(long id, UUID visitorId, String name, String normalizedName, String address, double lat, double lng,
                      String sido, String sigungu, String status, Long mergedInto, OffsetDateTime createdAt) {
    }

    private static final String SELECT = """
            SELECT id, visitor_id, name, normalized_name, address, lat, lng, sido, sigungu, status, merged_into, created_at
            FROM place_suggestion""";

    private static Row map(java.sql.ResultSet rs) throws java.sql.SQLException {
        long merged = rs.getLong("merged_into");
        return new Row(rs.getLong("id"), rs.getObject("visitor_id", UUID.class), rs.getString("name"), rs.getString("normalized_name"),
                rs.getString("address"), rs.getDouble("lat"), rs.getDouble("lng"), rs.getString("sido"), rs.getString("sigungu"),
                rs.getString("status"), rs.wasNull() ? null : merged, rs.getObject("created_at", OffsetDateTime.class));
    }

    /** 같은 정리한 이름의 제안 (거리는 부른 쪽에서 본다) */
    public List<Row> byName(String normalizedName) {
        return jdbc.sql(SELECT + " WHERE normalized_name = :name ORDER BY id")
                .param("name", normalizedName).query((rs, n) -> map(rs)).list();
    }

    public java.util.Optional<Row> find(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id").param("id", id).query((rs, n) -> map(rs)).optional();
    }

    public List<Row> byStatus(List<String> statuses, int limit) {
        return jdbc.sql(SELECT + " WHERE status IN (:statuses) ORDER BY id DESC LIMIT :limit")
                .param("statuses", statuses).param("limit", limit).query((rs, n) -> map(rs)).list();
    }

    /** 이 방문자가 최근 24시간에 알려 준 수 */
    public int countToday(UUID visitor) {
        return jdbc.sql("SELECT count(*) FROM place_suggestion WHERE visitor_id = :visitor AND created_at > now() - interval '1 day'")
                .param("visitor", visitor).query(Integer.class).single();
    }

    /** (lat, lng) 50m 안에 이 정리한 이름의 장소가 우리 데이터에 있으면 그 id */
    public java.util.Optional<Long> existingPlace(String normalizedName, double lat, double lng, double meters) {
        return jdbc.sql("""
                SELECT id FROM place
                WHERE closed_at IS NULL AND normalized_name = :name
                  AND lat BETWEEN :latMin AND :latMax AND lng BETWEEN :lngMin AND :lngMax
                ORDER BY id LIMIT 1""")
                .param("name", normalizedName)
                .param("latMin", lat - Geo.latDelta(meters)).param("latMax", lat + Geo.latDelta(meters))
                .param("lngMin", lng - Geo.lngDelta(meters, lat)).param("lngMax", lng + Geo.lngDelta(meters, lat))
                .query(Long.class).optional();
    }

    public long insert(UUID visitor, String name, String normalizedName, String address, double lat, double lng,
                       String sido, String sigungu, String status, Long mergedInto, Long placeId) {
        return jdbc.sql("""
                INSERT INTO place_suggestion (visitor_id, name, normalized_name, address, lat, lng, sido, sigungu, status, merged_into, place_id,
                                              decided_at)
                VALUES (:visitor, :name, :normalized, :address, :lat, :lng, :sido, :sigungu, :status, :merged, :place,
                        CASE WHEN :status IN ('pending', 'review') THEN NULL ELSE now() END)
                RETURNING id""")
                .param("visitor", visitor).param("name", name).param("normalized", normalizedName).param("address", address)
                .param("lat", lat).param("lng", lng).param("sido", sido).param("sigungu", sigungu)
                .param("status", status).param("merged", mergedInto).param("place", placeId)
                .query(Long.class).single();
    }

    public void setStatus(long id, String status, Long mergedInto) {
        jdbc.sql("UPDATE place_suggestion SET status = :status, merged_into = :merged, decided_at = now() WHERE id = :id")
                .param("id", id).param("status", status).param("merged", mergedInto).update();
    }

    /** 장소 찾기: (lat, lng) 에서 meters 안의 공개된 제안 중 말마다 정리한 이름에 든 것 */
    public List<Row> publicNear(double lat, double lng, double meters, List<String> words) {
        StringBuilder wordSql = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            wordSql.append(" AND normalized_name LIKE :w").append(i);
        }
        var spec = jdbc.sql(SELECT + " WHERE status = 'public'"
                        + " AND lat BETWEEN :latMin AND :latMax AND lng BETWEEN :lngMin AND :lngMax" + wordSql)
                .param("latMin", lat - Geo.latDelta(meters)).param("latMax", lat + Geo.latDelta(meters))
                .param("lngMin", lng - Geo.lngDelta(meters, lat)).param("lngMax", lng + Geo.lngDelta(meters, lat));
        for (int i = 0; i < words.size(); i++) {
            spec = spec.param("w" + i, "%" + words.get(i) + "%");
        }
        return spec.query((rs, n) -> map(rs)).list();
    }
}
