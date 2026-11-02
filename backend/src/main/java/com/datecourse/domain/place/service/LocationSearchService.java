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
import com.datecourse.domain.place.client.NaverLocalSearchClient;
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
 * 그래도 못 찾은 상권·건물 이름("안양일번가", "롯데월드몰")은 네이버 지역 검색으로 그 이름이 든 가게 자리를 쓴다 (searchNamed).
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
    private final NaverPlaceSearchService naver;
    private final int geocodeDailyLimit;

    public LocationSearchService(LocationSearchRepository repository, NaverGeocodeClient geocoder, JsonCache cache, NaverPlaceSearchService naver,
                                 @Value("${date-course.naver.geocode-daily-limit:3000}") int geocodeDailyLimit) {
        this.repository = repository;
        this.geocoder = geocoder;
        this.cache = cache;
        this.naver = naver;
        this.geocodeDailyLimit = geocodeDailyLimit;
    }

    /**
     * 상권·건물 이름을 네이버 지역 검색으로 ("안양일번가" → "KFC 안양일번가점" 자리). 가게 이름에 검색어가 들어 있는 첫 곳만 쓴다
     * (검색어와 상관없는 가게로 지도를 옮기지 않게). 지도에는 그 둘레 800m. 키가 없거나 하루 한도를 넘었으면 빈 목록.
     * 이름이 검색어와 똑같으면(지점명은 빼고) 그 가게 자체로 본다 (kind shop, placeId 없음: 우리 데이터에 없는 가게).
     * 우리 데이터·주소로 못 찾았을 때만 부른다 (네이버 하루 한도를 가게 찾기·사진 보기와 나눠 씀)
     */
    public List<LocationResult> searchNamed(String query) {
        String q = query == null ? "" : String.join(" ", query.strip().split("\\s+"));
        Query parsed = parse(q);
        String term = PlaceNames.normalize(parsed.term());
        if (term.length() < 2 || q.length() > 40) {
            return List.of();
        }
        try {
            for (NaverLocalSearchClient.Item item : naver.search(q)) {
                String title = PlaceNames.normalize(item.title());
                if (title.contains(term)) {
                    String address = item.roadAddress().isEmpty() ? item.address() : item.roadAddress();
                    if (title.equals(term) || PlaceNames.normalize(PlaceNames.splitBranch(item.title()).name()).equals(term)) {
                        return List.of(new LocationResult("shop", item.title(), address, item.lat(), item.lng(), 500, null, categoryOf(item.category())));
                    }
                    return List.of(new LocationResult("named", parsed.term(), address, item.lat(), item.lng(), 800));
                }
            }
        } catch (PlaceException e) {
            // 키가 없거나 오늘 한도를 다 씀
        }
        return List.of();
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

    /**
     * 우리 데이터로만 찾는 지역 검색 (Geocoding 을 부르지 않음): "AI 검색"가 문장의 낱말마다 지역인지 확인할 때.
     * 낱말마다 Geocoding 을 부르면 하루 한도를 낭비해서 따로 둔다. 2~40자가 아니면 빈 목록
     */
    public List<LocationResult> searchLocal(String query) {
        String q = query == null ? "" : String.join(" ", query.strip().split("\\s+"));
        if (q.length() < 2 || q.length() > 40) {
            return List.of();
        }
        return cache.getOrLoad("location-local:" + q, (List<LocationResult> r) -> r.isEmpty() ? EMPTY_TTL : CACHE_TTL,
                new TypeReference<List<LocationResult>>() { }, () -> load(q, false));
    }

    private List<LocationResult> load(String q) {
        return load(q, true);
    }

    private List<LocationResult> load(String q, boolean withGeocode) {
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
            result.addAll(repository.shops(normalized, query.sido()));
            if (normalized.length() >= 4 && normalized.endsWith("점")) {
                // "어니언 성수점" → 이름이 통째로 "어니언 성수"인 가게 (지점명을 이름에 붙여 둔 데이터가 있어서)
                result.addAll(repository.shops(normalized.substring(0, normalized.length() - 1), query.sido()));
            }
        }
        // "어니언 성수"처럼 체인 이름 + 지점·동네면 그 지점 (끝 낱말을 지점명·주소에서 찾음, "성수점"·"성수동" 모두)
        String[] words = query.term().split(" ");
        if (words.length >= 2) {
            String hint = areaPrefix(words[words.length - 1]).replaceAll("점$", "");
            String shopName = PlaceNames.normalize(String.join("", java.util.Arrays.copyOf(words, words.length - 1)));
            if (hint.length() >= 2 && shopName.length() >= 2) {
                result.addAll(repository.shopBranch(shopName, hint, query.sido()));
            }
        }
        if (withGeocode && geocoder.configured() && geocodeAllowed()) {
            for (NaverGeocodeClient.Address a : geocoder.geocode(q)) {
                boolean hasRoad = !a.roadAddress().isEmpty();
                result.add(new LocationResult("address", hasRoad ? a.roadAddress() : a.jibunAddress(),
                        hasRoad ? a.jibunAddress() : null, a.lat(), a.lng(), 500));
            }
        }
        if (withGeocode && result.isEmpty()) {
            result.addAll(searchNamed(q));
        }
        // 같은 이름·같은 동네가 여러 방법으로 찾아지면 하나만
        Set<String> seen = new HashSet<>();
        return result.stream().filter(r -> seen.add(r.kind() + "|" + r.name() + "|" + r.detail())).limit(MAX_RESULTS).toList();
    }

    /** 네이버 분류("카페,디저트>카페", "음식점>햄버거", "술집>요리주점") → 우리 분류 */
    static String categoryOf(String naverCategory) {
        String c = naverCategory == null ? "" : naverCategory;
        if (c.matches(".*(카페|디저트|베이커리|제과|커피|찻집).*")) {
            return "cafe";
        }
        if (c.matches(".*(술집|주점|호프|포차|이자카야|와인|칵테일|바\\b).*")) {
            return "bar";
        }
        if (c.matches(".*(음식점|한식|양식|일식|중식|분식|뷔페|패스트푸드|레스토랑|요리|식당|고기|해물|햄버거|피자|치킨).*")) {
            return "restaurant";
        }
        return "shop";
    }

    /** 오늘(한국 시간) Geocoding 을 몇 번 불렀는지 세어 한도 안이면 true. Redis 가 안 되면 캐시도 안 되므로 부르지 않는다 */
    private boolean geocodeAllowed() {
        long count = cache.increment("geocode-count:" + LocalDate.now(KST), Duration.ofDays(2));
        return count > 0 && count <= geocodeDailyLimit;
    }
}
