package com.datecourse.domain.ai.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.datecourse.domain.ai.exception.AiException;
import com.datecourse.global.cache.JsonCache;

/**
 * Ollama 를 부르기 전의 문. GPU(GTX 1650) 하나를 포트폴리오 챗봇과 나눠 쓰므로
 *   - 이 서버에서는 한 번에 하나만 (기다리다 WAIT 가 지나면 바쁜 것으로 보고 일반 순위로 넘어감)
 *   - 사이트 전체 하루 dailyLimit 번, 한 IP 한 시간 ipHourlyLimit 번 (넘으면 429)
 * Redis 가 안 되면 한도를 셀 수 없으므로 부르지 않는다 (일반 검색은 그대로).
 */
@Component
public class AiGate {

    static final Duration WAIT = Duration.ofSeconds(8);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final Semaphore one = new Semaphore(1, true);
    private final JsonCache cache;
    private final int dailyLimit;
    private final int ipHourlyLimit;

    public AiGate(JsonCache cache,
                  @Value("${date-course.ai.daily-limit:500}") int dailyLimit,
                  @Value("${date-course.ai.ip-hourly-limit:20}") int ipHourlyLimit) {
        this.cache = cache;
        this.dailyLimit = dailyLimit;
        this.ipHourlyLimit = ipHourlyLimit;
    }

    /** 한도를 세고(넘으면 429) 차례가 오면 work 를 부른다. 차례가 안 오거나 Redis 가 안 되면 null (바쁨) */
    public <T> T call(String ip, Supplier<T> work) {
        long perIp = cache.increment("ai-ip:" + (ip == null ? "-" : ip) + ":" + LocalDateTime.now(KST).getHour(), Duration.ofHours(1));
        if (perIp > ipHourlyLimit) {
            throw AiException.tooMany();
        }
        long today = cache.increment("ai-count:" + LocalDate.now(KST), Duration.ofDays(2));
        if (perIp <= 0 || today <= 0 || today > dailyLimit) {
            return null;
        }
        try {
            if (!one.tryAcquire(WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        try {
            return work.get();
        } finally {
            one.release();
        }
    }
}
