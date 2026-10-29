package com.datecourse.domain.place.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.datecourse.domain.place.client.NaverLocalSearchClient;
import com.datecourse.domain.place.client.NaverLocalSearchClient.Item;
import com.datecourse.domain.place.exception.PlaceException;
import com.datecourse.global.cache.JsonCache;

import tools.jackson.core.type.TypeReference;

/**
 * 네이버 지역 검색으로 가게 찾기 (우리 데이터에 없는 가게). 검색어마다 Redis 에 하루 두고,
 * 무료 한도를 넘지 않게 사이트 전체 하루 dailyLimit 번까지만 부른다.
 * 결과는 우리 장소 DB·이용자 제안에 쌓지 않는다 (사람이 고른 곳만 그 사람 일정에 저장).
 */
@Service
public class NaverPlaceSearchService {

    static final Duration CACHE_TTL = Duration.ofDays(1);
    static final Duration EMPTY_TTL = Duration.ofMinutes(10);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NaverLocalSearchClient client;
    private final JsonCache cache;
    private final int dailyLimit;

    public NaverPlaceSearchService(NaverLocalSearchClient client, JsonCache cache,
                                   @Value("${date-course.naver.search-daily-limit:20000}") int dailyLimit) {
        this.client = client;
        this.cache = cache;
        this.dailyLimit = dailyLimit;
    }

    /** 이미지 검색 (query: "가게 이름 시도 시군구"). 지역 검색과 같은 하루 한도를 나눠 쓴다. 키가 없거나 한도를 넘으면 503 */
    public List<NaverLocalSearchClient.Image> images(String query) {
        if (!client.configured()) {
            throw PlaceException.imagesUnavailable();
        }
        return cache.getOrLoad("naver-image:" + query, (List<NaverLocalSearchClient.Image> r) -> r.isEmpty() ? EMPTY_TTL : CACHE_TTL,
                new TypeReference<List<NaverLocalSearchClient.Image>>() { }, () -> {
                    if (!allowed()) {
                        throw PlaceException.imagesUnavailable();
                    }
                    return client.images(query);
                });
    }

    /** 오늘(한국 시간) 검색 API 를 부른 수를 세어 한도 안이면 true. Redis 가 안 되면 캐시·한도도 안 되므로 부르지 않는다 */
    private boolean allowed() {
        long count = cache.increment("naver-search-count:" + LocalDate.now(KST), Duration.ofDays(2));
        return count > 0 && count <= dailyLimit;
    }

    /** query 는 "동네 + 가게 이름" (예: "울산광역시 북구 카페 높"). 키가 없거나 오늘 한도를 다 썼으면 503 */
    public List<Item> search(String query) {
        if (!client.configured()) {
            throw PlaceException.searchUnavailable();
        }
        return cache.getOrLoad("naver-local:" + query, (List<Item> r) -> r.isEmpty() ? EMPTY_TTL : CACHE_TTL,
                new TypeReference<List<Item>>() { }, () -> {
                    if (!allowed()) {
                        throw PlaceException.searchUnavailable();
                    }
                    return client.search(query);
                });
    }
}
