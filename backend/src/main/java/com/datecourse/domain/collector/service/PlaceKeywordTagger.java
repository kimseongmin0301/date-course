package com.datecourse.domain.collector.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.datecourse.domain.collector.util.PlaceKeywordRules;
import com.datecourse.domain.place.entity.PlaceCategory;

/**
 * 모든 장소에 키워드를 자동으로 다시 붙인다 (PlaceKeywordRules).
 * id 구간마다 한 트랜잭션: 그 구간의 auto 키워드를 지우고 새로 넣는다. 관리자가 붙인 것(admin)은 두고,
 * 관리자가 실내외·예산을 정해 둔 장소에는 그 종류의 auto 키워드를 넣지 않는다 (하나만 있어야 해서).
 * 몇 번을 돌려도 결과가 같아서, 규칙을 고친 뒤나 수집이 끝난 뒤 다시 돌리면 된다.
 */
@Service
public class PlaceKeywordTagger {

    private static final Logger log = LoggerFactory.getLogger(PlaceKeywordTagger.class);
    private static final long RANGE = 20_000;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public PlaceKeywordTagger(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** 붙인 키워드 수 */
    public long tagAll() {
        long started = System.currentTimeMillis();
        Map<String, Long> keywordIds = new HashMap<>();
        jdbc.query("SELECT id, code FROM keyword", rs -> {
            keywordIds.put(rs.getString("code"), rs.getLong("id"));
        });
        Long maxId = jdbc.queryForObject("SELECT max(id) FROM place", Long.class);
        long total = 0;
        for (long start = 1; maxId != null && start <= maxId; start += RANGE) {
            long from = start;
            long to = start + RANGE - 1;
            total += tx.execute(status -> tagRange(from, to, keywordIds));
            if ((to / RANGE) % 10 == 0 || to >= maxId) {
                log.info("키워드 붙이기: id {}/{} 까지, 키워드 {}개", Math.min(to, maxId), maxId, total);
            }
        }
        log.info("키워드 붙이기 끝: {}개 ({}초)", total, (System.currentTimeMillis() - started) / 1000);
        return total;
    }

    private long tagRange(long from, long to, Map<String, Long> keywordIds) {
        jdbc.update("DELETE FROM place_keyword WHERE origin = 'auto' AND place_id BETWEEN ? AND ?", from, to);
        List<Object[]> rows = new ArrayList<>();
        jdbc.query("""
                SELECT id, category, detail_category, normalized_name FROM place
                WHERE id BETWEEN ? AND ? AND closed_at IS NULL""", rs -> {
            long placeId = rs.getLong("id");
            PlaceCategory category = PlaceCategory.valueOf(rs.getString("category").toUpperCase());
            for (String code : PlaceKeywordRules.codesFor(category, rs.getString("detail_category"), rs.getString("normalized_name"))) {
                Long keywordId = keywordIds.get(code);
                if (keywordId == null) {
                    throw new IllegalStateException("keyword 테이블에 없는 code: " + code);
                }
                rows.add(new Object[] {placeId, keywordId});
            }
        }, from, to);
        jdbc.batchUpdate("""
                INSERT INTO place_keyword (place_id, keyword_id, origin) VALUES (?, ?, 'auto')
                ON CONFLICT (place_id, keyword_id) DO NOTHING""", rows);
        // 관리자가 실내외·예산을 정해 둔 장소: 같은 종류의 auto 는 뺀다
        jdbc.update("""
                DELETE FROM place_keyword a USING keyword ka, place_keyword m, keyword km
                WHERE a.place_id BETWEEN ? AND ? AND a.origin = 'auto' AND a.keyword_id = ka.id
                  AND ka.kind IN ('setting', 'budget')
                  AND m.place_id = a.place_id AND m.origin = 'admin' AND m.keyword_id = km.id AND km.kind = ka.kind""", from, to);
        return rows.size();
    }
}
