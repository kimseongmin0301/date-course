package com.datecourse.domain.account.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 다른 기기에서도 보기 계정 (plan_account, V6). 엔티티 없이 JdbcClient 로만 쓴다 */
@Repository
public class AccountRepository {

    private final JdbcClient jdbc;

    public AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Row(long id, String code, UUID visitorId, String passwordHash) {
    }

    private static Row map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(rs.getLong("id"), rs.getString("code"), rs.getObject("visitor_id", UUID.class), rs.getString("password_hash"));
    }

    public Optional<Row> byCode(String code) {
        return jdbc.sql("SELECT id, code, visitor_id, password_hash FROM plan_account WHERE code = :code")
                .param("code", code).query((rs, n) -> map(rs)).optional();
    }

    public Optional<Row> byVisitor(UUID visitor) {
        return jdbc.sql("SELECT id, code, visitor_id, password_hash FROM plan_account WHERE visitor_id = :visitor")
                .param("visitor", visitor).query((rs, n) -> map(rs)).optional();
    }

    /** 코드가 이미 있으면 false (드물게 겹치면 새 코드로 다시) */
    public boolean insert(String code, UUID visitor, String passwordHash) {
        return jdbc.sql("""
                INSERT INTO plan_account (code, visitor_id, password_hash) VALUES (:code, :visitor, :hash)
                ON CONFLICT (code) DO NOTHING""")
                .param("code", code).param("visitor", visitor).param("hash", passwordHash).update() > 0;
    }

    public void touchLogin(long id) {
        jdbc.sql("UPDATE plan_account SET last_login_at = now() WHERE id = :id").param("id", id).update();
    }
}
