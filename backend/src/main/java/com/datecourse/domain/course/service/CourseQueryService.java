package com.datecourse.domain.course.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.datecourse.domain.collector.util.Geo;
import com.datecourse.domain.collector.util.PlaceNames;
import com.datecourse.domain.course.dto.AlternativeResponse;
import com.datecourse.domain.course.dto.CourseCard;
import com.datecourse.domain.course.dto.CourseDetailResponse;
import com.datecourse.domain.course.dto.CourseSearchRequest;
import com.datecourse.domain.course.dto.CourseSearchResponse;
import com.datecourse.domain.course.dto.PlaceResponse;
import com.datecourse.domain.course.dto.StepDetail;
import com.datecourse.domain.course.exception.CourseException;
import com.datecourse.domain.course.entity.StepRole;
import com.datecourse.domain.course.repository.CourseQueryRepository;
import com.datecourse.domain.course.util.CoursePlanner;
import com.datecourse.domain.place.entity.PlaceCategory;
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
        return reasons(s.badge(), s.tour(), s.sbiz(), s.franchise(), s.category());
    }

    static List<String> reasons(boolean badge, boolean tour, boolean sbiz, boolean franchise, String category) {
        List<String> reasons = new ArrayList<>();
        if (badge) {
            reasons.add("모범음식점");
        }
        if (tour && sbiz) {
            reasons.add("관광공사·상가정보 모두 확인");
        } else if (tour) {
            reasons.add("관광공사 소개");
        }
        if (!franchise && LOCAL_SHOP.contains(category)) {
            reasons.add("체인 아닌 동네 가게");
        }
        return reasons;
    }

    /** 근처 대안: 원래 장소에서 이만큼 안 (걸어서 약 10분) */
    static final double ALTERNATIVE_RADIUS_M = 600;
    public static final int ALTERNATIVES_MAX = 5;

    /**
     * 코스 order 번째 단계의 근처 대안 (같은 역할: 구경·카페·식당), 코스 만들 때와 같은 점수로 좋은 순 5곳.
     * 이미 코스에 든 곳(같은 가게 포함)은 뺀다. 결과는 1시간 캐시
     */
    public List<AlternativeResponse> alternatives(long courseId, int order) {
        return cache.getOrLoad("courses:" + courseId + ":alt:" + order, DETAIL_TTL,
                new TypeReference<List<AlternativeResponse>>() { }, () -> {
                    CourseDetailResponse course = detail(courseId);
                    StepDetail step = course.steps().stream().filter(s -> s.order() == order).findFirst()
                            .orElseThrow(CourseException::notFound);
                    StepRole role = StepRole.valueOf(step.role().toUpperCase());
                    PlaceResponse origin = step.place();
                    List<CoursePlanner.Spot> inCourse = course.steps().stream().map(s -> toSpot(s.place())).toList();
                    Set<String> courseKeywords = Set.copyOf(course.keywords());
                    return repository.candidates(origin.lat(), origin.lng(), ALTERNATIVE_RADIUS_M, ROLE_CATEGORIES.get(role)).stream()
                            .map(c -> {
                                CoursePlanner.Spot spot = toSpot(c);
                                double meters = Geo.distanceMeters(origin.lat(), origin.lng(), c.lat(), c.lng());
                                boolean ok = meters <= ALTERNATIVE_RADIUS_M && CoursePlanner.fitsRole(spot, role)
                                        && !CoursePlanner.sameAsAny(spot, inCourse);
                                return ok ? new Scored(c, meters, CoursePlanner.alternativeScore(spot, role, meters, courseKeywords)) : null;
                            })
                            .filter(Objects::nonNull)
                            .sorted(Comparator.comparingDouble((Scored x) -> -x.score()).thenComparingLong(x -> x.c().id()))
                            .limit(ALTERNATIVES_MAX)
                            .map(x -> new AlternativeResponse(toPlace(x.c()),
                                    reasons(x.c().badge(), x.c().tour(), x.c().sbiz(), x.c().franchise(), x.c().category()),
                                    (int) Math.round(x.meters())))
                            .toList();
                });
    }

    private record Scored(CourseQueryRepository.Candidate c, double meters, double score) {
    }

    /** 코스에 넣을 장소 찾기: 이 위치에서 2km 안, 이름에 검색어가 든 곳을 가까운 순으로 */
    static final double NEARBY_RADIUS_M = 2000;
    public static final int NEARBY_MAX = 10;
    private static final List<String> ALL_CATEGORIES = List.of("attraction", "culture", "activity", "shop", "cafe", "restaurant", "bar");

    public List<AlternativeResponse> nearbyPlaces(Double lat, Double lng, String query) {
        if (lat == null || lng == null || !Geo.inKorea(lat, lng)) {
            throw CourseException.badRequest("지도에서 국내 지역을 골라 주세요.");
        }
        String q = PlaceNames.normalize(query == null ? "" : query.strip());
        if (q.isEmpty() || q.length() > 30) {
            throw CourseException.badRequest("찾을 가게나 장소 이름을 적어 주세요.");
        }
        double la = Math.round(lat * 1000) / 1000.0;
        double ln = Math.round(lng * 1000) / 1000.0;
        return cache.getOrLoad("places:nearby:" + la + ":" + ln + ":" + q, SEARCH_TTL, new TypeReference<List<AlternativeResponse>>() { },
                () -> repository.candidates(la, ln, NEARBY_RADIUS_M, ALL_CATEGORIES, q).stream()
                        .map(c -> new Scored(c, Geo.distanceMeters(la, ln, c.lat(), c.lng()), 0))
                        .filter(x -> x.meters() <= NEARBY_RADIUS_M)
                        // 이름이 같은 곳 → 이름이 검색어로 시작 → 업종이 검색어("서점") → 이름에 들어 있기만("세무서점"), 그 안에서 가까운 순
                        .sorted(Comparator.comparingInt((Scored x) -> nearbyRank(x.c(), q)).thenComparingDouble(Scored::meters))
                        .limit(NEARBY_MAX)
                        .map(x -> new AlternativeResponse(toPlace(x.c()),
                                reasons(x.c().badge(), x.c().tour(), x.c().sbiz(), x.c().franchise(), x.c().category()),
                                (int) Math.round(x.meters())))
                        .toList());
    }

    static int nearbyRank(CourseQueryRepository.Candidate c, String q) {
        String name = c.normalizedName();
        if (name.equals(q)) {
            return 0;
        }
        if (name.startsWith(q)) {
            return 1;
        }
        String detail = c.detailCategory() == null ? "" : c.detailCategory().replace(" ", "");
        return detail.contains(q) ? 2 : 3;
    }

    private static final Map<StepRole, List<String>> ROLE_CATEGORIES = Map.of(
            StepRole.SPOT, List.of("attraction", "culture", "shop", "activity"),
            StepRole.CAFE, List.of("cafe"),
            StepRole.MEAL, List.of("restaurant"));

    private static CoursePlanner.Spot toSpot(CourseQueryRepository.Candidate c) {
        return new CoursePlanner.Spot(c.id(), c.name(), c.normalizedName(), PlaceCategory.valueOf(c.category().toUpperCase()),
                c.detailCategory(), c.lat(), c.lng(), c.sido(), c.sigungu(), c.keywords(), c.tour(), c.franchise(), c.badge());
    }

    /** 코스에 든 장소 (같은 가게 비교용이라 이름·위치만) */
    private static CoursePlanner.Spot toSpot(PlaceResponse p) {
        return new CoursePlanner.Spot(p.id(), p.name(), PlaceNames.normalize(p.name()), PlaceCategory.valueOf(p.category().toUpperCase()),
                p.detailCategory(), p.lat(), p.lng(), p.sido(), p.sigungu(), Set.of(), false, false, false);
    }

    private static PlaceResponse toPlace(CourseQueryRepository.Candidate c) {
        return new PlaceResponse(c.id(), c.name(), c.branch(), c.category(), c.detailCategory(), c.lat(), c.lng(),
                c.sido(), c.sigungu(), c.roadAddress(), c.lotAddress(), c.phone(), c.imageUrl(), c.description(),
                naverMapUrl(c.name(), c.branch(), c.sigungu()));
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
