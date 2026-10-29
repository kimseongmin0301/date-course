package com.datecourse.domain.place.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.datecourse.domain.collector.util.PlaceNames;
import com.datecourse.domain.collector.util.Regions;
import com.datecourse.domain.place.client.NaverGeocodeClient;
import com.datecourse.domain.place.dto.AddressResult;
import com.datecourse.domain.place.dto.LocationResult;
import com.datecourse.domain.place.exception.PlaceException;
import com.datecourse.domain.place.repository.LocationSearchRepository;
import com.datecourse.global.cache.JsonCache;

import tools.jackson.core.type.TypeReference;

/**
 * 지역 검색: "성수동", "강남구" 같은 지역 이름, "반석역" 같은 역, "별마당도서관" 같은 명소, "강남대로 396" 같은 주소.
 * 지역·역·명소는 우리 장소 데이터로(무료), 주소는 네이버 Geocoding 으로 찾는다 (Geocoding 은 역·가게 이름은 못 찾음).
 * "대전 반석역"처럼 앞에 시도를 붙이면 그 시도 안에서만 찾는다 (같은 이름의 역·동이 여러 도시에 있어서).
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

    /** 검색어 = (시도) + 찾을 말. 시도는 뒤에 말이 더 있을 때만 본다 ("서울숲" 은 시도가 아님) */
    record Query(String sido, String term) {
    }

    static Query parse(String q) {
        String[] tokens = q.split(" ", 2);
        if (tokens.length == 2 && tokens[0].length() <= 5) {
            String sido = Regions.canonicalSido(tokens[0]);
            if (sido != null) {
                return new Query(sido, tokens[1]);
            }
        }
        return new Query(null, q);
    }

    /** 끝의 시·군·구·동·읍·면·역을 뗀다 (두 글자 이상 남을 때만) */
    static String areaPrefix(String q) {
        String compact = q.replace(" ", "");
        if (compact.length() >= 3 && "시군구동읍면역".indexOf(compact.charAt(compact.length() - 1)) >= 0) {
            return compact.substring(0, compact.length() - 1);
        }
        return compact;
    }

    public List<LocationResult> search(String query) {
        String q = clean(query);
        return cache.getOrLoad("location:" + q, (List<LocationResult> r) -> r.isEmpty() ? EMPTY_TTL : CACHE_TTL,
                new TypeReference<List<LocationResult>>() { }, () -> load(q));
    }

    /**
     * 주소 찾기 (데이터에 없는 가게를 일정에 직접 넣을 때). 네이버 Geocoding 만 쓰고 한도·캐시는 지역 검색과 같다.
     * 키가 없거나 오늘 한도를 다 썼으면 503 (빈 목록이면 "못 찾음"과 헷갈려서)
     */
    public List<AddressResult> addresses(String query) {
        String q = query == null ? "" : String.join(" ", query.strip().split("\\s+"));
        if (q.length() < 2 || q.length() > 60) {
            throw PlaceException.badAddress();
        }
        if (!geocoder.configured()) {
            throw PlaceException.addressUnavailable();
        }
        return cache.getOrLoad("address:" + q, (List<AddressResult> r) -> r.isEmpty() ? EMPTY_TTL : CACHE_TTL,
                new TypeReference<List<AddressResult>>() { }, () -> {
                    if (!geocodeAllowed()) {
                        throw PlaceException.addressUnavailable();
                    }
                    return geocoder.geocode(q).stream()
                            .map(a -> new AddressResult(a.roadAddress(), a.jibunAddress(), a.lat(), a.lng()))
                            .toList();
                });
    }

    private List<LocationResult> load(String q) {
        Query query = parse(q);
        String term = query.term().replace(" ", "");
        String base = areaPrefix(query.term());
        List<LocationResult> result = new ArrayList<>();
        if (term.length() >= 2 && term.endsWith("역")) {
            result.addAll(repository.stations(term, PlaceNames.normalize(term), query.sido(), 3));
        }
        result.addAll(repository.areas(base, query.sido(), 4));
        if (base.length() >= 2 && !term.matches(".*[시군구]$")) {
            // 시·군·구가 아니면 법정동도 본다 ("반석역" → 반석동: 행정동으로는 노은3동이라 위에서 안 나옴)
            result.addAll(repository.legalDongs(base + "동", query.sido(), 2));
        }
        String normalized = PlaceNames.normalize(query.term());
        if (normalized.length() >= 2) {
            result.addAll(repository.landmarks(normalized, query.sido(), 5));
        }
        if (geocoder.configured() && geocodeAllowed()) {
            for (NaverGeocodeClient.Address a : geocoder.geocode(q)) {
                boolean hasRoad = !a.roadAddress().isEmpty();
                result.add(new LocationResult("address", hasRoad ? a.roadAddress() : a.jibunAddress(),
                        hasRoad ? a.jibunAddress() : null, a.lat(), a.lng(), 500));
            }
        }
        // 같은 이름·같은 동네가 여러 방법으로 찾아지면 하나만
        Set<String> seen = new HashSet<>();
        return result.stream().filter(r -> seen.add(r.kind() + "|" + r.name() + "|" + r.detail())).limit(MAX_RESULTS).toList();
    }

    /** 오늘(한국 시간) Geocoding 을 몇 번 불렀는지 세어 한도 안이면 true. Redis 가 안 되면 캐시도 안 되므로 부르지 않는다 */
    private boolean geocodeAllowed() {
        long count = cache.increment("geocode-count:" + LocalDate.now(KST), Duration.ofDays(2));
        return count > 0 && count <= geocodeDailyLimit;
    }
}
