package com.datecourse.domain.course.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.datecourse.domain.collector.util.Geo;
import com.datecourse.domain.course.dto.CourseCard;
import com.datecourse.domain.course.dto.CourseDetailResponse;
import com.datecourse.domain.course.dto.CourseSearchRequest;
import com.datecourse.domain.course.dto.CourseSearchResponse;
import com.datecourse.domain.course.dto.PlaceResponse;
import com.datecourse.domain.course.dto.StepDetail;
import com.datecourse.domain.course.exception.CourseException;
import com.datecourse.domain.course.repository.CourseQueryRepository;
import com.datecourse.domain.keyword.dto.KeywordResponse;
import com.datecourse.domain.keyword.service.KeywordService;
import com.datecourse.global.cache.JsonCache;

import tools.jackson.core.type.TypeReference;

/** 코스 검색(지도 가운데 + 반경 + 키워드)과 상세. 결과는 Redis 에 잠깐 둔다 (점수는 하루 한 번 바뀜) */
@Service
public class CourseQueryService {

    public static final int RADIUS_MIN = 500;
    public static final int RADIUS_MAX = 20_000;
    public static final int KEYWORDS_MAX = 3;
    public static final int PAGE_MAX = 9;
    /** 고른 반경에 코스가 없으면 두 배씩 넓혀 이 반경까지 찾는다 */
    public static final int WIDEN_MAX = 10_000;
    static final Duration SEARCH_TTL = Duration.ofMinutes(10);
    static final Duration DETAIL_TTL = Duration.ofHours(1);

    private final CourseQueryRepository repository;
    private final KeywordService keywordService;
    private final JsonCache cache;

    public CourseQueryService(CourseQueryRepository repository, KeywordService keywordService, JsonCache cache) {
        this.repository = repository;
        this.keywordService = keywordService;
        this.cache = cache;
    }

    /** 입력을 확인하고 캐시 키가 같아지게 정리한다 (키워드 소문자·중복 제거·정렬) */
    public CourseSearchRequest normalize(Double lat, Double lng, Integer radius, List<String> keywords, Integer page) {
        if (lat == null || lng == null || !Geo.inKorea(lat, lng)) {
            throw CourseException.badRequest("지도에서 국내 지역을 골라 주세요.");
        }
        int r = radius == null ? 3000 : radius;
        if (r < RADIUS_MIN || r > RADIUS_MAX) {
            throw CourseException.badRequest("반경은 " + RADIUS_MIN + "m 에서 " + RADIUS_MAX / 1000 + "km 사이로 골라 주세요.");
        }
        int p = page == null ? 0 : page;
        if (p < 0 || p > PAGE_MAX) {
            throw CourseException.badRequest("더 볼 코스가 없어요.");
        }
        List<String> codes = (keywords == null ? List.<String>of() : keywords).stream()
                .flatMap(k -> List.of(k.split(",")).stream())
                .map(String::strip).filter(k -> !k.isEmpty()).map(String::toLowerCase)
                .distinct().sorted().toList();
        if (codes.size() > KEYWORDS_MAX) {
            throw CourseException.badRequest("키워드는 " + KEYWORDS_MAX + "개까지 고를 수 있어요.");
        }
        Set<String> known = keywordService.all().stream().map(KeywordResponse::code).collect(Collectors.toSet());
        if (!known.containsAll(codes)) {
            throw CourseException.badRequest("모르는 키워드가 있어요.");
        }
        return new CourseSearchRequest(lat, lng, r, codes, p);
    }

    public CourseSearchResponse search(CourseSearchRequest req) {
        return cache.getOrLoad(req.cacheKey(), SEARCH_TTL, new TypeReference<CourseSearchResponse>() { }, () -> load(req));
    }

    private CourseSearchResponse load(CourseSearchRequest req) {
        int size = CourseSearchRequest.PAGE_SIZE;
        int radius = req.radius();
        List<CourseQueryRepository.Row> rows = repository.search(req.latKey(), req.lngKey(), radius, req.keywords(),
                size, req.page() * size);
        // 첫 쪽이 비면(근처에 코스가 없는 동네) 넓혀서라도 가까운 코스를 보여준다
        while (rows.isEmpty() && req.page() == 0 && radius < WIDEN_MAX) {
            radius = Math.min(radius * 2, WIDEN_MAX);
            rows = repository.search(req.latKey(), req.lngKey(), radius, req.keywords(), size, 0);
        }
        List<Long> ids = rows.stream().map(CourseQueryRepository.Row::id).toList();
        Map<Long, List<CourseQueryRepository.StepPlace>> steps = repository.steps(ids);
        Map<Long, List<String>> keywords = repository.keywords(ids);
        List<CourseCard> cards = rows.stream().map(r -> {
            List<String> codes = keywords.getOrDefault(r.id(), List.of());
            return new CourseCard(r.id(), r.title(), r.summary(), r.kind(), r.sido(), r.sigungu(), r.centerLat(), r.centerLng(),
                    r.totalMinutes(), (int) Math.round(r.distance()), codes,
                    codes.stream().filter(req.keywords()::contains).toList(),
                    steps.getOrDefault(r.id(), List.of()).stream().map(CourseQueryRepository.StepPlace::summary).toList());
        }).toList();
        int total = rows.isEmpty() ? 0 : rows.get(0).total();
        return new CourseSearchResponse(total, req.page(), size, radius, radius != req.radius(), cards);
    }

    public CourseDetailResponse detail(long id) {
        return cache.getOrLoad("courses:" + id, DETAIL_TTL, new TypeReference<CourseDetailResponse>() { }, () -> {
            CourseQueryRepository.Detail c = repository.detail(id).orElseThrow(CourseException::notFound);
            List<StepDetail> steps = repository.steps(List.of(id)).getOrDefault(id, List.of()).stream()
                    .map(s -> new StepDetail(s.order(), s.role(), s.stayMinutes(), s.walkMinutes(), s.memo(), toPlace(s), reasons(s)))
                    .toList();
            return new CourseDetailResponse(c.id(), c.title(), c.summary(), c.kind(), c.sido(), c.sigungu(),
                    c.centerLat(), c.centerLng(), c.totalMinutes(), c.budgetMin(), c.budgetMax(),
                    repository.keywords(List.of(id)).getOrDefault(id, List.of()), steps);
        });
    }

    /** 이 장소를 고른 이유 (화면에 작은 표시로). 리뷰·별점이 없으므로 우리가 아는 사실만 쓴다 */
    static List<String> reasons(CourseQueryRepository.StepPlace s) {
        List<String> reasons = new ArrayList<>();
        if (s.badge()) {
            reasons.add("모범음식점");
        }
        if (s.tour() && s.sbiz()) {
            reasons.add("관광공사·상가정보 모두 확인");
        } else if (s.tour()) {
            reasons.add("관광공사 소개");
        }
        if (!s.franchise() && LOCAL_SHOP.contains(s.category())) {
            reasons.add("체인 아닌 동네 가게");
        }
        return reasons;
    }

    private static final Set<String> LOCAL_SHOP = Set.of("cafe", "restaurant", "shop", "bar");

    static PlaceResponse toPlace(CourseQueryRepository.StepPlace s) {
        return new PlaceResponse(s.placeId(), s.name(), s.branch(), s.category(), s.detailCategory(), s.lat(), s.lng(),
                s.sido(), s.sigungu(), s.roadAddress(), s.lotAddress(), s.phone(), s.imageUrl(), s.description(),
                naverMapUrl(s.name(), s.branch(), s.sigungu()));
    }

    /** 네이버지도 검색 주소: "이름 지점 시군구" (같은 이름이 많아도 그 동네 것이 먼저 나오게) */
    static String naverMapUrl(String name, String branch, String sigungu) {
        String query = String.join(" ", java.util.stream.Stream.of(name, branch, sigungu)
                .filter(v -> v != null && !v.isBlank()).toList());
        return "https://map.naver.com/p/search/" + URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
