package com.datecourse.domain.place.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.datecourse.domain.collector.util.PlaceNames;
import com.datecourse.domain.place.client.NaverGeocodeClient;
import com.datecourse.domain.place.dto.LocationResult;
import com.datecourse.domain.place.exception.PlaceException;
import com.datecourse.domain.place.repository.LocationSearchRepository;
import com.datecourse.global.cache.JsonCache;

import tools.jackson.core.type.TypeReference;

/**
 * 지역 검색: "성수동", "강남구" 같은 지역 이름, "별마당도서관" 같은 명소, "강남대로 396" 같은 주소.
 * 지역·명소는 우리 장소 데이터로(무료), 주소는 네이버 Geocoding 으로 찾는다.
 * Geocoding 은 무료 이용량(월 300만 건)을 넘지 않게 사이트 전체 하루 geocodeDailyLimit 번까지만 부르고,
 * 결과는 검색어마다 Redis 에 하루 둔다.
 */
@Service
public class LocationSearchService {

    public static final int MAX_RESULTS = 10;
    static final Duration CACHE_TTL = Duration.ofDays(1);
    /** 못 찾은 결과는 짧게 둔다 (Geocoding 이 잠깐 안 됐거나 키를 막 넣은 경우 하루 내내 빈 결과가 나가지 않게) */
    static final Duration EMPTY_TTL = Duration.ofMinutes(10);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LocationSearchRepository repository;
    private final NaverGeocodeClient geocoder;
    private final JsonCache cache;
    private final int geocodeDailyLimit;

    public LocationSearchService(LocationSearchRepository repository, NaverGeocodeClient geocoder, JsonCache cache,
                                 @Value("${date-course.naver.geocode-daily-limit:3000}") int geocodeDailyLimit) {
        this.repository = repository;
        this.geocoder = geocoder;
        this.cache = cache;
        this.geocodeDailyLimit = geocodeDailyLimit;
    }

    /** 앞뒤 공백을 떼고 가운데 공백은 하나로. 2~40자 */
    static String clean(String query) {
        String q = query == null ? "" : String.join(" ", query.strip().split("\\s+"));
        if (q.length() < 2 || q.length() > 40) {
            throw PlaceException.badQuery();
        }
        return q;
    }

    /** 끝의 시·군·구·동·읍·면을 뗀다 (두 글자 이상 남을 때만) */
    static String areaPrefix(String q) {
        String compact = q.replace(" ", "");
        if (compact.length() >= 3 && "시군구동읍면".indexOf(compact.charAt(compact.length() - 1)) >= 0) {
            return compact.substring(0, compact.length() - 1);
        }
        return compact;
    }

    public List<LocationResult> search(String query) {
        String q = clean(query);
        return cache.getOrLoad("location:" + q, (List<LocationResult> r) -> r.isEmpty() ? EMPTY_TTL : CACHE_TTL,
                new TypeReference<List<LocationResult>>() { }, () -> load(q));
    }

    private List<LocationResult> load(String q) {
        List<LocationResult> result = new ArrayList<>(repository.areas(areaPrefix(q), 4));
        String normalized = PlaceNames.normalize(q);
        if (normalized.length() >= 2) {
            result.addAll(repository.landmarks(normalized, 5));
        }
        if (geocoder.configured() && geocodeAllowed()) {
            for (NaverGeocodeClient.Address a : geocoder.geocode(q)) {
                boolean hasRoad = !a.roadAddress().isEmpty();
                result.add(new LocationResult("address", hasRoad ? a.roadAddress() : a.jibunAddress(),
                        hasRoad ? a.jibunAddress() : null, a.lat(), a.lng(), 500));
            }
        }
        return result.stream().limit(MAX_RESULTS).toList();
    }

    /** 오늘(한국 시간) Geocoding 을 몇 번 불렀는지 세어 한도 안이면 true. Redis 가 안 되면 캐시도 안 되므로 부르지 않는다 */
    private boolean geocodeAllowed() {
        long count = cache.increment("geocode-count:" + LocalDate.now(KST), Duration.ofDays(2));
        return count > 0 && count <= geocodeDailyLimit;
    }
}
