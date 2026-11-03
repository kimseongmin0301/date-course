package com.datecourse.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.datecourse.domain.weather.client.OpenMeteoClient;
import com.datecourse.domain.weather.exception.WeatherException;
import com.datecourse.global.cache.JsonCache;

import tools.jackson.databind.json.JsonMapper;

class WeatherServiceTests {

    OpenMeteoClient client = mock(OpenMeteoClient.class);
    JsonCache cache = mock(JsonCache.class);
    WeatherService service = new WeatherService(client, cache, 10);
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @SuppressWarnings("unchecked")
    void passThroughCache() {
        given(cache.getOrLoad(any(), any(java.util.function.Function.class), any(), any())).willAnswer(inv -> ((Supplier<Object>) inv.getArgument(3)).get());
        given(cache.increment(any(), any())).willReturn(1L);
    }

    @Test
    void 비가_오거나_비_올_확률이_높으면_rainy() {
        var rain = WeatherService.toResponse(today, new OpenMeteoClient.Daily(63, 18.4, 11.6, 80));
        assertThat(rain.label()).isEqualTo("비");
        assertThat(rain.rainy()).isTrue();
        assertThat(rain.tempMax()).isEqualTo(18);
        assertThat(rain.tempMin()).isEqualTo(12);

        assertThat(WeatherService.toResponse(today, new OpenMeteoClient.Daily(3, 15, 9, 65)).rainy()).isTrue();    // 흐림이어도 65%
        assertThat(WeatherService.toResponse(today, new OpenMeteoClient.Daily(0, 20, 10, 10)).rainy()).isFalse();
        assertThat(WeatherService.look(73)[0]).isEqualTo("눈");
    }

    @Test
    void 예보가_있는_날만_부르고_좌표는_동네_단위로() {
        passThroughCache();
        given(client.daily(anyDouble(), anyDouble(), any())).willReturn(Optional.of(new OpenMeteoClient.Daily(1, 20, 10, 20)));

        assertThat(service.forecast(37.54321, 127.05678, today.toString()).available()).isTrue();
        verify(client).daily(37.5, 127.1, today);   // 소수 첫째 자리만 보낸다

        assertThat(service.forecast(37.5, 127.0, today.plusDays(30).toString()).available()).isFalse();   // 너무 먼 날
        assertThat(service.forecast(37.5, 127.0, today.minusDays(1).toString()).available()).isFalse();   // 지난 날
        assertThatThrownBy(() -> service.forecast(37.5, 127.0, "11/08")).isInstanceOf(WeatherException.class);
        assertThatThrownBy(() -> service.forecast(10.0, 10.0, today.toString())).isInstanceOf(WeatherException.class);
    }

    @Test
    void 예보_응답_읽기() throws Exception {
        var root = JsonMapper.builder().build().readTree("""
                {"daily":{"time":["2026-11-08"],"weather_code":[61],"temperature_2m_max":[16.2],"temperature_2m_min":[9.8],
                 "precipitation_probability_max":[85]}}""");
        assertThat(OpenMeteoClient.parse(root)).hasValueSatisfying(d -> {
            assertThat(d.code()).isEqualTo(61);
            assertThat(d.rainProbability()).isEqualTo(85);
        });
    }
}
