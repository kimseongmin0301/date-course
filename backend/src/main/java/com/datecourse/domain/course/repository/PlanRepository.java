package com.datecourse.domain.course.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 내 일정 저장 (saved_plan, V4). 엔티티 없이 JdbcClient 로만 쓴다 (steps 는 JSONB 문자열) */
@Repository
public class PlanRepository {

    private final JdbcClient jdbc;

    public PlanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Row(long courseId, String title, String sido, String sigungu, LocalDate date, int startMinute,
                      String stepsJson, OffsetDateTime updatedAt) {
    }

    private static final String SELECT = """
            SELECT sp.course_id, c.title, c.sido, c.sigungu, sp.plan_date, sp.start_minute, sp.steps::text AS steps, sp.updated_at
            FROM saved_plan sp JOIN course c ON c.id = sp.course_id
            WHERE sp.visitor_id = :visitor""";

    private static Row map(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Date date = rs.getDate("plan_date");
        return new Row(rs.getLong("course_id"), rs.getString("title"), rs.getString("sido"), rs.getString("sigungu"),
                date == null ? null : date.toLocalDate(), rs.getInt("start_minute"), rs.getString("steps"),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    public Optional<Row> find(UUID visitor, long courseId) {
        return jdbc.sql(SELECT + " AND sp.course_id = :course")
                .param("visitor", visitor).param("course", courseId)
                .query((rs, n) -> map(rs)).optional();
    }

    public List<Row> list(UUID visitor, int limit) {
        return jdbc.sql(SELECT + " ORDER BY sp.updated_at DESC LIMIT :limit")
                .param("visitor", visitor).param("limit", limit)
                .query((rs, n) -> map(rs)).list();
    }

    public int count(UUID visitor) {
        return jdbc.sql("SELECT count(*) FROM saved_plan WHERE visitor_id = :visitor")
                .param("visitor", visitor).query(Integer.class).single();
    }

    public void upsert(UUID visitor, long courseId, LocalDate date, int startMinute, String stepsJson) {
        jdbc.sql("""
                INSERT INTO saved_plan (visitor_id, course_id, plan_date, start_minute, steps)
                VALUES (:visitor, :course, :date, :start, CAST(:steps AS jsonb))
                ON CONFLICT (visitor_id, course_id) DO UPDATE SET plan_date = EXCLUDED.plan_date,
                    start_minute = EXCLUDED.start_minute, steps = EXCLUDED.steps, updated_at = now()""")
                .param("visitor", visitor).param("course", courseId)
                .param("date", date == null ? null : java.sql.Date.valueOf(date))
                .param("start", startMinute).param("steps", stepsJson)
                .update();
    }

    public boolean delete(UUID visitor, long courseId) {
        return jdbc.sql("DELETE FROM saved_plan WHERE visitor_id = :visitor AND course_id = :course")
                .param("visitor", visitor).param("course", courseId).update() > 0;
    }

    /**
     * from 방문자의 일정을 모두 to 로 옮긴다 (다른 기기에서 로그인할 때). 같은 코스가 둘 다 있으면 나중에 저장한 쪽을 남긴다.
     * 옮긴 수(from 에 있던 일정 수)를 돌려준다
     */
    public int move(UUID from, UUID to) {
        int count = jdbc.sql("SELECT count(*) FROM saved_plan WHERE visitor_id = :from").param("from", from).query(Integer.class).single();
        if (count == 0) {
            return 0;
        }
        jdbc.sql("""
                INSERT INTO saved_plan (visitor_id, course_id, plan_date, start_minute, steps, created_at, updated_at)
                SELECT :to, course_id, plan_date, start_minute, steps, created_at, updated_at FROM saved_plan WHERE visitor_id = :from
                ON CONFLICT (visitor_id, course_id) DO UPDATE SET plan_date = EXCLUDED.plan_date, start_minute = EXCLUDED.start_minute,
                    steps = EXCLUDED.steps, updated_at = EXCLUDED.updated_at
                WHERE saved_plan.updated_at < EXCLUDED.updated_at""")
                .param("from", from).param("to", to).update();
        jdbc.sql("DELETE FROM saved_plan WHERE visitor_id = :from").param("from", from).update();
        return count;
    }

    /** 공유 링크 만들기 (공유할 때의 일정 그대로) */
    public void insertShare(String token, UUID visitor, long courseId, LocalDate date, int startMinute, String stepsJson, OffsetDateTime expiresAt) {
        jdbc.sql("""
                INSERT INTO plan_share (token, visitor_id, course_id, plan_date, start_minute, steps, expires_at)
                VALUES (:token, :visitor, :course, :date, :start, CAST(:steps AS jsonb), :expires)""")
                .param("token", token).param("visitor", visitor).param("course", courseId)
                .param("date", date == null ? null : java.sql.Date.valueOf(date))
                .param("start", startMinute).param("steps", stepsJson).param("expires", expiresAt)
                .update();
    }

    /** 공유 링크로 일정 읽기 (만료됐거나 숨긴 코스면 없음). updatedAt 칸에는 만든 시각 */
    public Optional<Row> findShare(String token) {
        return jdbc.sql("""
                SELECT ps.course_id, c.title, c.sido, c.sigungu, ps.plan_date, ps.start_minute, ps.steps::text AS steps, ps.created_at AS updated_at
                FROM plan_share ps JOIN course c ON c.id = ps.course_id
                WHERE ps.token = :token AND ps.expires_at > now() AND NOT c.hidden""")
                .param("token", token)
                .query((rs, n) -> map(rs)).optional();
    }

    /** 이 방문자가 하루 동안 만든 공유 링크 수 */
    public int sharesToday(UUID visitor) {
        return jdbc.sql("SELECT count(*) FROM plan_share WHERE visitor_id = :visitor AND created_at > now() - interval '1 day'")
                .param("visitor", visitor).query(Integer.class).single();
    }

    /** 만료된 공유 링크 지우기. 지운 수 */
    public int deleteExpiredShares() {
        return jdbc.sql("DELETE FROM plan_share WHERE expires_at <= now()").update();
    }

    public boolean courseExists(long courseId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM course WHERE id = :id)").param("id", courseId).query(Boolean.class).single();
    }
}
