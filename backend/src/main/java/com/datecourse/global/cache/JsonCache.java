package com.datecourse.global.cache;

import java.time.Duration;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis 캐시 (힐링룸과 같은 Redis·계정, 키는 모두 {prefix} = healing-room:date-course: 로 시작).
 * 값은 JSON 문자열로, 모든 값에 수명(TTL)을 준다.
 * Redis 가 안 되면 캐시를 건너뛰고 loader 를 그대로 부른다 (서비스는 계속 돈다).
 */
@Component
public class JsonCache {

    private static final Logger log = LoggerFactory.getLogger(JsonCache.class);

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final String prefix;

    public JsonCache(StringRedisTemplate redis, JsonMapper json,
                     @Value("${date-course.redis.key-prefix}") String prefix) {
        this.redis = redis;
        this.json = json;
        this.prefix = prefix;
    }

    /** 캐시에 있으면 꺼내고, 없으면 loader 로 만들어 ttl 동안 넣어 둔다 */
    public <T> T getOrLoad(String key, Duration ttl, TypeReference<T> type, Supplier<T> loader) {
        String fullKey = prefix + key;
        try {
            String cached = redis.opsForValue().get(fullKey);
            if (cached != null) {
                return json.readValue(cached, type);
            }
        } catch (RuntimeException e) {
            log.warn("캐시 읽기 실패 {}: {}", fullKey, e.getMessage());
        }
        T value = loader.get();
        try {
            redis.opsForValue().set(fullKey, json.writeValueAsString(value), ttl);
        } catch (RuntimeException e) {
            log.warn("캐시 쓰기 실패 {}: {}", fullKey, e.getMessage());
        }
        return value;
    }

    /** 원본이 바뀌었을 때 지운다 */
    public void evict(String key) {
        try {
            redis.delete(prefix + key);
        } catch (RuntimeException e) {
            log.warn("캐시 지우기 실패 {}: {}", prefix + key, e.getMessage());
        }
    }
}
