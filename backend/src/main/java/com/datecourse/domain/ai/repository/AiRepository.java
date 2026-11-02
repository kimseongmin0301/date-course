package com.datecourse.domain.ai.repository;

import java.sql.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** AI 추천용 코스 글·임베딩 (course_embedding, V7). 엔티티 없이 JdbcClient 로만 쓴다 */
@Repository
public class AiRepository {

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    public AiRepository(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 임베딩할 코스 한 줄: 지역, 제목, 단계(장소 이름·업종), 키워드 이름 */
    public record CourseText(long id, String title, String sido, String sigungu, Integer totalMinutes, List<String> steps, String keywords) {
    }

    /** 숨기지 않은 모든 코스의 글 재료 */
    public List<CourseText> courseTexts() {
        return jdbc.sql("""
                SELECT c.id, c.title, c.sido, c.sigungu, c.total_minutes,
                       (SELECT string_agg(p.name || '|' || p.category || '|' || coalesce(p.detail_category, ''), E'\\n' ORDER BY s.step_order)
                        FROM course_step s JOIN place p ON p.id = s.place_id WHERE s.course_id = c.id) AS steps,
                       (SELECT string_agg(k.name, ', ' ORDER BY k.kind, k.sort_order)
                        FROM course_keyword ck JOIN keyword k ON k.id = ck.keyword_id WHERE ck.course_id = c.id) AS keywords
                FROM course c WHERE NOT c.hidden ORDER BY c.id""")
                .query((rs, n) -> {
                    String steps = rs.getString("steps");
                    int minutes = rs.getInt("total_minutes");
                    return new CourseText(rs.getLong("id"), rs.getString("title"), rs.getString("sido"), rs.getString("sigungu"),
                            rs.wasNull() ? null : minutes, steps == null ? List.of() : List.of(steps.split("\n")), rs.getString("keywords"));
                })
                .list();
    }

    /** 코스 id → 지금 임베딩의 글 해시 (이 모델로 만든 것만) */
    public Map<Long, String> hashes(String model) {
        Map<Long, String> out = new HashMap<>();
        jdbc.sql("SELECT course_id, text_hash FROM course_embedding WHERE model = :model").param("model", model)
                .query(rs -> {
                    out.put(rs.getLong(1), rs.getString(2));
                });
        return out;
    }

    /** 임베딩 한 개 (저장할 것) */
    public record Row(long courseId, String hash, float[] vector) {
    }

    private static final String UPSERT = """
            INSERT INTO course_embedding (course_id, model, text_hash, vector) VALUES (?, ?, ?, CAST(? AS real[]))
            ON CONFLICT (course_id) DO UPDATE SET model = EXCLUDED.model, text_hash = EXCLUDED.text_hash,
                vector = EXCLUDED.vector, updated_at = now()""";

    /** 여러 개를 한 번에 (수집기를 로컬에서 돌리면 운영 DB 까지 왕복이 길어서 한 줄씩 넣으면 느림) */
    public void upsertAll(String model, List<Row> rows) {
        jdbcTemplate.batchUpdate(UPSERT, rows, rows.size(), (ps, r) -> {
            ps.setLong(1, r.courseId());
            ps.setString(2, model);
            ps.setString(3, r.hash());
            ps.setString(4, literal(r.vector()));
        });
    }

    /** PostgreSQL 배열 글자 "{0.1,0.2,…}" (드라이버마다 float[] 매핑이 달라서 글자로 넘긴다) */
    static String literal(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 12).append('{');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append('}').toString();
    }

    /** 메모리 색인에 올릴 것: 숨기지 않은 코스의 id·가운데 좌표·벡터 */
    public record Stored(long id, double lat, double lng, float[] vector) {
    }

    public List<Stored> loadAll(String model) {
        List<Stored> out = new ArrayList<>();
        jdbc.sql("""
                SELECT e.course_id, c.center_lat, c.center_lng, e.vector FROM course_embedding e JOIN course c ON c.id = e.course_id
                WHERE e.model = :model AND NOT c.hidden""")
                .param("model", model)
                .query(rs -> {
                    Array array = rs.getArray("vector");
                    Object[] values = (Object[]) array.getArray();
                    float[] v = new float[values.length];
                    for (int i = 0; i < v.length; i++) {
                        v[i] = ((Number) values[i]).floatValue();
                    }
                    out.add(new Stored(rs.getLong(1), rs.getDouble(2), rs.getDouble(3), v));
                });
        return out;
    }
}
