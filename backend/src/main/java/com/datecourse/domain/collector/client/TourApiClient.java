package com.datecourse.domain.collector.client;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.JsonNode;

/**
 * 한국관광공사 TourAPI (국문 관광정보 서비스, KorService2).
 * 일반 인증키는 Decoding 키를 받아 여기서 한 번만 인코딩한다 (+, /, = 가 들어 있어서)
 */
@Component
public class TourApiClient {

    private final RestClient client;
    private final String baseUrl;
    private final String serviceKey;

    public TourApiClient(@Value("${date-course.tour.base-url}") String baseUrl,
                         @Value("${date-course.tour.service-key:}") String serviceKey) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.serviceKey = serviceKey == null ? "" : serviceKey.strip();
    }

    public boolean configured() {
        return !serviceKey.isEmpty();
    }

    /** 지역 기반 목록 한 쪽. areaCode 가 null 이면 전국 */
    public Page areaBasedList(int contentTypeId, Integer areaCode, int pageNo, int numOfRows) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(baseUrl + "/areaBasedList2")
                .queryParam("serviceKey", URLEncoder.encode(serviceKey, StandardCharsets.UTF_8))
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "date-course")
                .queryParam("_type", "json")
                .queryParam("arrange", "C")
                .queryParam("contentTypeId", contentTypeId)
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", numOfRows);
        if (areaCode != null) {
            uri.queryParam("areaCode", areaCode);
        }
        URI url = uri.build(true).toUri();

        JsonNode body = client.get().uri(url).retrieve().body(JsonNode.class);
        JsonNode response = body == null ? null : body.path("response");
        String resultCode = response == null ? "" : response.path("header").path("resultCode").asString("");
        if (!"0000".equals(resultCode)) {
            String message = response == null ? "응답 없음" : response.path("header").path("resultMsg").asString("");
            throw new IllegalStateException("TourAPI 오류 " + resultCode + " " + message);
        }
        JsonNode bodyNode = response.path("body");
        List<JsonNode> items = new ArrayList<>();
        // 결과가 없으면 items 가 빈 문자열("")로 온다
        for (JsonNode item : bodyNode.path("items").path("item")) {
            items.add(item);
        }
        return new Page(bodyNode.path("totalCount").asInt(0), items);
    }

    public record Page(int totalCount, List<JsonNode> items) {
    }
}
