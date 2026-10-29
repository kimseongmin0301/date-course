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

import com.datecourse.domain.place.exception.PlaceException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * NAVER API HUB 검색 API 의 지역 검색 (가게·기관 이름 → 업체 정보)과 이미지 검색. Client Secret 이 필요해서 서버에서만 부른다.
 * 한 번에 최대 5곳, 기준 좌표를 넘길 수 없어 검색어에 동네를 붙여 부른다.
 * 무료: 0~775,000건, 하루 최대 25,000건. 키가 없으면 빈 목록, 부르다 실패하면 503 (빈 결과로 캐시되지 않게).
 * 응답은 JSON 인데 Content-Type 이 text/plain 으로 와서 글자로 받아 직접 읽는다.
 */
@Component
public class NaverLocalSearchClient {

    private static final Logger log = LoggerFactory.getLogger(NaverLocalSearchClient.class);
    public static final int MAX_DISPLAY = 5;

    /** title 은 태그를 지운 이름, category 는 "카페,디저트>카페" 같은 분류 */
    public record Item(String title, String category, String address, String roadAddress, double lat, double lng) {
    }

    /** 이미지 검색 결과: title 은 이미지가 실린 문서의 제목, thumbnail 은 네이버가 줄인 사진 (https 만) */
    public record Image(String title, String thumbnail, int width, int height) {
    }

    public static final int IMAGE_DISPLAY = 6;

    private final RestClient client;
    private final String baseUrl;
    private final String imageUrl;
    private final String clientId;
    private final String clientSecret;
    private final JsonMapper json;

    public NaverLocalSearchClient(@Value("${date-course.naver.search-url}") String baseUrl,
                                  @Value("${date-course.naver.search-client-id:}") String clientId,
                                  @Value("${date-course.naver.search-client-secret:}") String clientSecret,
                                  @Value("${date-course.naver.image-url:https://naverapihub.apigw.ntruss.com/search/v1/image}") String imageUrl,
                                  JsonMapper json) {
        this.json = json;
        this.imageUrl = imageUrl;
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

    public List<Item> search(String query) {
        if (!configured()) {
            return List.of();
        }
        try {
            String text = client.get()
                    .uri(UriComponentsBuilder.fromUriString(baseUrl).queryParam("query", query)
                            .queryParam("display", MAX_DISPLAY).queryParam("start", 1).queryParam("sort", "random")
                            .queryParam("format", "json")
                            .encode().build().toUri())
                    .header("X-NCP-APIGW-API-KEY-ID", clientId)
                    .header("X-NCP-APIGW-API-KEY", clientSecret)
                    .retrieve()
                    .body(String.class);
            return parse(text);
        } catch (RuntimeException e) {
            log.warn("네이버 지역 검색 실패: {}", e.getMessage());
            throw PlaceException.searchUnavailable();
        }
    }

    /** 이미지 검색 (정확도 순 IMAGE_DISPLAY 장). 키가 없으면 빈 목록, 실패하면 503 */
    public List<Image> images(String query) {
        if (!configured()) {
            return List.of();
        }
        try {
            String text = client.get()
                    .uri(UriComponentsBuilder.fromUriString(imageUrl).queryParam("query", query)
                            .queryParam("display", IMAGE_DISPLAY).queryParam("start", 1).queryParam("sort", "sim")
                            .queryParam("filter", "all").queryParam("format", "json")
                            .encode().build().toUri())
                    .header("X-NCP-APIGW-API-KEY-ID", clientId)
                    .header("X-NCP-APIGW-API-KEY", clientSecret)
                    .retrieve()
                    .body(String.class);
            return parseImages(text);
        } catch (RuntimeException e) {
            log.warn("네이버 이미지 검색 실패: {}", e.getMessage());
            throw PlaceException.imagesUnavailable();
        }
    }

    /** 응답 JSON → 사진 목록 (https 섬네일만) */
    List<Image> parseImages(String text) {
        List<Image> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        for (JsonNode i : json.readTree(text).path("items")) {
            String thumbnail = i.path("thumbnail").asString("");
            if (thumbnail.startsWith("https://")) {
                result.add(new Image(plain(i.path("title").asString("")), thumbnail,
                        i.path("sizewidth").asInt(0), i.path("sizeheight").asInt(0)));
            }
        }
        return result;
    }

    /** 응답 JSON → 업체 목록 (좌표·이름이 없는 것은 뺀다) */
    List<Item> parse(String text) {
        List<Item> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        JsonNode body = json.readTree(text);
        for (JsonNode i : body.path("items")) {
            double lng = coordinate(i.path("mapx").asString(""));
            double lat = coordinate(i.path("mapy").asString(""));
            String title = plain(i.path("title").asString(""));
            if (lat != 0 && lng != 0 && !title.isEmpty()) {
                result.add(new Item(title, plain(i.path("category").asString("")), i.path("address").asString(""),
                        i.path("roadAddress").asString(""), lat, lng));
            }
        }
        return result;
    }

    /** 좌표 글자 → 도 단위. 문서는 WGS84 라고 하지만 예전 검색 API 처럼 1e7 을 곱한 정수("1270000000")로 오면 나눈다 */
    static double coordinate(String text) {
        try {
            double v = Double.parseDouble(text.strip());
            return Math.abs(v) > 1000 ? v / 1e7 : v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 검색어를 강조하는 <b> 같은 태그와 &amp; 같은 글자 참조를 지운다 */
    static String plain(String html) {
        return html.replaceAll("<[^>]*>", "")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .strip();
    }
}
