package com.datecourse.domain.place.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.JsonNode;

/**
 * 네이버 클라우드 Maps Geocoding (주소 → 좌표). Client Secret 이 필요해서 서버에서만 부른다.
 * 무료 이용량은 대표 계정 1개에 월 3,000,000건. 키가 없거나 오류면 빈 목록 (다른 검색은 그대로).
 */
@Component
public class NaverGeocodeClient {

    private static final Logger log = LoggerFactory.getLogger(NaverGeocodeClient.class);

    public record Address(String roadAddress, String jibunAddress, double lat, double lng) {
    }

    private final RestClient client;
    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;

    public NaverGeocodeClient(@Value("${date-course.naver.geocode-url}") String baseUrl,
                              @Value("${date-course.naver.client-id:}") String clientId,
                              @Value("${date-course.naver.client-secret:}") String clientSecret) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl;
        this.clientId = clientId == null ? "" : clientId.strip();
        this.clientSecret = clientSecret == null ? "" : clientSecret.strip();
    }

    public boolean configured() {
        return !clientId.isEmpty() && !clientSecret.isEmpty();
    }

    public List<Address> geocode(String query) {
        if (!configured()) {
            return List.of();
        }
        try {
            JsonNode body = client.get()
                    .uri(UriComponentsBuilder.fromUriString(baseUrl).queryParam("query", query).queryParam("count", 5)
                            .encode().build().toUri())
                    .header("x-ncp-apigw-api-key-id", clientId)
                    .header("x-ncp-apigw-api-key", clientSecret)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(JsonNode.class);
            List<Address> result = new ArrayList<>();
            if (body == null || !"OK".equals(body.path("status").asString(""))) {
                return result;
            }
            for (JsonNode a : body.path("addresses")) {
                double lng = a.path("x").asDouble(0);
                double lat = a.path("y").asDouble(0);
                if (lat != 0 && lng != 0) {
                    result.add(new Address(a.path("roadAddress").asString(""), a.path("jibunAddress").asString(""), lat, lng));
                }
            }
            return result;
        } catch (RuntimeException e) {
            log.warn("Geocoding 실패: {}", e.getMessage());
            return List.of();
        }
    }
}
