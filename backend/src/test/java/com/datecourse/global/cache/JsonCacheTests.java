package com.datecourse.global.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class JsonCacheTests {

    record Item(String code, String name) {
    }

    static final TypeReference<List<Item>> TYPE = new TypeReference<>() { };
    static final Duration TTL = Duration.ofMinutes(5);

    StringRedisTemplate redis;
    ValueOperations<String, String> values;
    JsonCache cache;
    AtomicInteger loads = new AtomicInteger();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        given(redis.opsForValue()).willReturn(values);
        cache = new JsonCache(redis, JsonMapper.builder().build(), "healing-room:date-course:");
    }

    List<Item> load() {
        loads.incrementAndGet();
        return List.of(new Item("quiet", "조용"));
    }

    @Test
    void 없으면_만들어서_접두어_붙인_키에_TTL_과_함께_넣는다() {
        assertThat(cache.getOrLoad("keywords", TTL, TYPE, this::load)).containsExactly(new Item("quiet", "조용"));

        assertThat(loads).hasValue(1);
        verify(values).set(eq("healing-room:date-course:keywords"), eq("[{\"code\":\"quiet\",\"name\":\"조용\"}]"), eq(TTL));
    }

    @Test
    void 있으면_꺼내고_다시_만들지_않는다() {
        given(values.get("healing-room:date-course:keywords")).willReturn("[{\"code\":\"walk\",\"name\":\"산책\"}]");

        assertThat(cache.getOrLoad("keywords", TTL, TYPE, this::load)).containsExactly(new Item("walk", "산책"));
        assertThat(loads).hasValue(0);
        verify(values, never()).set(anyString(), anyString(), eq(TTL));
    }

    @Test
    void Redis_가_안_되면_캐시를_건너뛰고_그대로_만든다() {
        given(values.get(anyString())).willThrow(new RedisConnectionFailureException("down"));

        assertThat(cache.getOrLoad("keywords", TTL, TYPE, this::load)).containsExactly(new Item("quiet", "조용"));
        assertThat(loads).hasValue(1);
    }
}
