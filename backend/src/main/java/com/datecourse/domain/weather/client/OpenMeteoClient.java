package com.datecourse.domain.weather.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Open-Meteo 일기 예보 (키 없음, 비상업 무료, 하루 10,000번 안, 데이터는 CC BY 4.0 이라 화면에 출처를 적는다).
 * 그날 하루치(날씨 코드, 최고·최저 기온, 비 올 확률)만 받는다. 예보는 오늘부터 16일까지.
 * 서버에서만 부르고 보내는 것은 코스 가운데 좌표(소수 첫째 자리, 약 10km)와 날짜뿐이다.
 */
@Component
public class OpenMeteoClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);

    /** code: WMO 날씨 코드, rainProbability: 그날 가장 높은 비 올 확률(%) */
    public record Daily(int code, double tempMax, double tempMin, Integer rainProbability) {
    }

    private final RestClient client;
    private final String baseUrl;
    private final boolean enabled;
    private final JsonMapper json;

    public OpenMeteoClient(@Value("${date-course.weather.url:https://api.open-meteo.com/v1/forecast}") String baseUrl,
                           @Value("${date-course.weather.enabled:true}") boolean enabled, JsonMapper json) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl;
        this.enabled = enabled;
        this.json = json;
    }

    public boolean enabled() {
        return enabled;
    }

    /** 그날 예보. 꺼져 있거나 실패하면 빈 값 */
    public Optional<Daily> daily(double lat, double lng, LocalDate date) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            String text = client.get()
                    .uri(UriComponentsBuilder.fromUriString(baseUrl)
                            .queryParam("latitude", lat).queryParam("longitude", lng)
                            .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                            .queryParam("timezone", "Asia/Seoul")
                            .queryParam("start_date", date).queryParam("end_date", date)
                            .encode().build().toUri())
                    .retrieve().body(String.class);
            return parse(text == null ? null : json.readTree(text));
        } catch (RuntimeException e) {
            log.warn("일기 예보를 받지 못함: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public static Optional<Daily> parse(JsonNode root) {
        JsonNode d = root == null ? null : root.path("daily");
        if (d == null || !d.path("weather_code").isArray() || d.path("weather_code").isEmpty() || d.path("weather_code").get(0).isNull()) {
            return Optional.empty();
        }
        JsonNode rain = d.path("precipitation_probability_max").path(0);
        return Optional.of(new Daily(d.path("weather_code").get(0).asInt(), d.path("temperature_2m_max").path(0).asDouble(),
                d.path("temperature_2m_min").path(0).asDouble(), rain.isNumber() ? rain.asInt() : null));
    }
}
