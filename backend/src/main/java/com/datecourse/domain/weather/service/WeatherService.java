package com.datecourse.domain.weather.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.datecourse.domain.collector.util.Geo;
import com.datecourse.domain.weather.client.OpenMeteoClient;
import com.datecourse.domain.weather.dto.WeatherResponse;
import com.datecourse.domain.weather.exception.WeatherException;
import com.datecourse.global.cache.JsonCache;

import tools.jackson.core.type.TypeReference;

/**
 * 코스 날짜의 날씨 (Open-Meteo). 같은 동네(소수 첫째 자리, 약 10km)·같은 날은 Redis 에 3시간 두고(못 받았으면 10분),
 * 사이트 전체 하루 dailyLimit 번까지만 부른다(무료 하루 10,000번 안). 예보는 오늘부터 FORECAST_DAYS 일까지.
 */
@Service
public class WeatherService {

    static final int FORECAST_DAYS = 15;
    static final Duration TTL = Duration.ofHours(3);
    static final Duration EMPTY_TTL = Duration.ofMinutes(10);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 비·눈·뇌우 (WMO 코드) */
    private static final Set<Integer> WET = Set.of(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99);
    static final int RAINY_PROBABILITY = 60;

    private final OpenMeteoClient client;
    private final JsonCache cache;
    private final int dailyLimit;

    public WeatherService(OpenMeteoClient client, JsonCache cache, @Value("${date-course.weather.daily-limit:5000}") int dailyLimit) {
        this.client = client;
        this.cache = cache;
        this.dailyLimit = dailyLimit;
    }

    public WeatherResponse forecast(Double lat, Double lng, String date) {
        if (lat == null || lng == null || !Geo.inKorea(lat, lng)) {
            throw WeatherException.badRequest("국내 위치만 날씨를 볼 수 있어요.");
        }
        LocalDate day;
        try {
            day = LocalDate.parse(date == null ? "" : date);
        } catch (DateTimeParseException e) {
            throw WeatherException.badRequest("날짜를 2026-11-08 처럼 적어 주세요.");
        }
        LocalDate today = LocalDate.now(KST);
        if (day.isBefore(today) || day.isAfter(today.plusDays(FORECAST_DAYS))) {
            return WeatherResponse.none(day.toString());   // 지난 날이나 너무 먼 날은 예보가 없다
        }
        double la = Math.round(lat * 10) / 10.0;
        double ln = Math.round(lng * 10) / 10.0;
        return cache.getOrLoad("weather:" + la + ":" + ln + ":" + day, (WeatherResponse w) -> w.available() ? TTL : EMPTY_TTL,
                new TypeReference<WeatherResponse>() { }, () -> {
                    if (!allowed()) {
                        return WeatherResponse.none(day.toString());
                    }
                    return client.daily(la, ln, day).map(d -> toResponse(day, d)).orElse(WeatherResponse.none(day.toString()));
                });
    }

    static WeatherResponse toResponse(LocalDate day, OpenMeteoClient.Daily d) {
        String[] look = look(d.code());
        boolean rainy = WET.contains(d.code()) || (d.rainProbability() != null && d.rainProbability() >= RAINY_PROBABILITY);
        return new WeatherResponse(true, day.toString(), look[0], look[1], d.rainProbability(),
                (int) Math.round(d.tempMin()), (int) Math.round(d.tempMax()), rainy);
    }

    /** WMO 날씨 코드 → [이름, 그림] */
    static String[] look(int code) {
        if (code == 0) {
            return new String[] {"맑음", "☀️"};
        }
        if (code <= 2) {
            return new String[] {"구름 조금", "🌤️"};
        }
        if (code == 3) {
            return new String[] {"흐림", "☁️"};
        }
        if (code == 45 || code == 48) {
            return new String[] {"안개", "🌫️"};
        }
        if (code >= 51 && code <= 57) {
            return new String[] {"이슬비", "🌦️"};
        }
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) {
            return new String[] {"비", "🌧️"};
        }
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            return new String[] {"눈", "🌨️"};
        }
        if (code >= 95) {
            return new String[] {"뇌우", "⛈️"};
        }
        return new String[] {"흐림", "☁️"};
    }

    /** 오늘(한국 시간) 부른 수가 한도 안이면 true. Redis 가 안 되면 캐시도 안 되므로 부르지 않는다 */
    private boolean allowed() {
        long count = cache.increment("weather-count:" + LocalDate.now(KST), Duration.ofDays(2));
        return count > 0 && count <= dailyLimit;
    }
}
