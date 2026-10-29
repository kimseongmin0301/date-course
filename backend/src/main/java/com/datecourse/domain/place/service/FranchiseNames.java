package com.datecourse.domain.place.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.datecourse.global.cache.JsonCache;

import tools.jackson.core.type.TypeReference;

/**
 * 체인점 판별 (정리한 이름 = PlaceNames.normalize 기준). 코스 만들기·코스 상세·근처 대안이 모두 이것을 쓴다.
 *  1) 흔한 이름: 같은 이름이 전국에 COMMON_MIN 곳 넘게 (김밥천국, 페리카나 … 그리고 "전주식당" 같은 흔한 이름도)
 *  2) 브랜드 + 짧은 꼬리: 지점명이 자주 붙는 이름(브랜드)으로 시작하고 뒤에 지역·지점명이 짧게 붙은 이름
 *     ("투썸플레이스서초", "할리스대전건양대", "투다리용죽점"). 상가정보에는 지점명이 이름에 붙어 들어간 가게가 많아서
 *  브랜드 = 흔한 이름 중 지점명이 있는 비율이 BRAND_BRANCH_RATIO 이상이고 BRAND_MIN_LENGTH 자 이상
 *  ("우리식당"·"아지트"처럼 흔하지만 지점이 없는 이름은 브랜드가 아니라서 "우리식당본점" 같은 동네 가게는 안 걸림).
 *
 * 이름 목록을 만드는 집계는 장소 92만 곳을 훑어 30초 넘게 걸리므로 Redis 에 하루 두고(서버를 다시 켜도 바로),
 * 웹 서버는 켜질 때 뒤에서 미리 읽는다. 아직 못 읽었으면 기다리지 않고 "체인 아님"으로 본다 (화면이 멈추지 않게).
 */
@Component
public class FranchiseNames {

    private static final Logger log = LoggerFactory.getLogger(FranchiseNames.class);

    public static final int COMMON_MIN = 15;
    static final double BRAND_BRANCH_RATIO = 0.3;
    static final int BRAND_MIN_LENGTH = 3;
    /** 브랜드 뒤에 붙는 지역·지점명 길이 (정리한 이름 기준, "대전건양대" 5자) */
    static final int TAIL_MAX = 6;
    static final String CACHE_KEY = "franchise-names";
    private static final Duration TTL = Duration.ofDays(1);

    private final JdbcTemplate jdbc;
    private final JsonCache cache;
    private final AtomicBoolean loading = new AtomicBoolean();
    private volatile Names names;

    public FranchiseNames(JdbcTemplate jdbc, JsonCache cache) {
        this.jdbc = jdbc;
        this.cache = cache;
    }

    record Names(Set<String> common, Set<String> brands, Instant loadedAt) {
    }

    /** Redis 에 두는 모양 */
    record Stored(List<String> common, List<String> brands) {
    }

    public boolean isFranchise(String normalizedName) {
        Names n = names;
        if (n == null || n.loadedAt().plus(TTL).isBefore(Instant.now())) {
            warmInBackground();
        }
        return n != null && isFranchise(normalizedName, n);
    }

    static boolean isFranchise(String name, Names names) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (names.common().contains(name)) {
            return true;
        }
        int from = Math.max(BRAND_MIN_LENGTH, name.length() - TAIL_MAX);
        for (int end = from; end < name.length(); end++) {
            if (names.brands().contains(name.substring(0, end))) {
                return true;
            }
        }
        return false;
    }

    /** 장소를 다시 수집한 뒤(자동 코스 만들기 전에): Redis 를 거치지 않고 다시 집계해 둘 다 바꾼다 */
    public void reload() {
        cache.evict(CACHE_KEY);
        names = toNames(cache.getOrLoad(CACHE_KEY, TTL, new TypeReference<Stored>() { }, this::aggregate));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmInBackground() {
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                names = toNames(cache.getOrLoad(CACHE_KEY, TTL, new TypeReference<Stored>() { }, this::aggregate));
            } catch (RuntimeException e) {
                log.warn("체인 판별 목록을 읽지 못했어요: {}", e.getMessage());
            } finally {
                loading.set(false);
            }
        });
    }

    private static Names toNames(Stored s) {
        return new Names(Set.copyOf(s.common()), Set.copyOf(s.brands()), Instant.now());
    }

    private Stored aggregate() {
        long started = System.currentTimeMillis();
        List<String> common = new ArrayList<>();
        Set<String> brands = new HashSet<>();
        jdbc.query("""
                SELECT normalized_name, count(*) AS n, count(branch) AS b FROM place
                WHERE closed_at IS NULL GROUP BY normalized_name HAVING count(*) > ?""", rs -> {
            String name = rs.getString("normalized_name");
            common.add(name);
            if (name.length() >= BRAND_MIN_LENGTH && rs.getDouble("b") / rs.getDouble("n") >= BRAND_BRANCH_RATIO) {
                brands.add(name);
            }
        }, COMMON_MIN);
        log.info("체인 판별: 흔한 이름 {}개, 브랜드 {}개 ({}ms)", common.size(), brands.size(), System.currentTimeMillis() - started);
        return new Stored(common, new ArrayList<>(brands));
    }
}
