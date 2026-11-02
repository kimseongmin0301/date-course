package com.datecourse.domain.ai.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.datecourse.domain.ai.client.OllamaClient;
import com.datecourse.domain.ai.dto.AiRecommendRequest;
import com.datecourse.domain.ai.dto.AiRecommendResponse;
import com.datecourse.domain.course.dto.CourseCard;
import com.datecourse.domain.course.dto.CourseDetailResponse;
import com.datecourse.domain.course.dto.CourseSearchRequest;
import com.datecourse.domain.course.dto.CourseSearchResponse;
import com.datecourse.domain.course.service.CourseQueryService;
import com.datecourse.domain.keyword.dto.KeywordResponse;
import com.datecourse.domain.keyword.service.KeywordService;
import com.datecourse.global.cache.JsonCache;
import com.datecourse.global.exception.ApiException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

/**
 * AI 추천 (실험 기능, RAG): 요청 문장에 맞는 코스를 "후보 안에서만" 고른다. 사용자가 누구인지 모르므로(첫 데이트·오래된 커플…)
 * 추측하지 않고, 조건에 맞는 것 중 인기 있을 법한(순위가 높은) 코스를 먼저 고르게 한다.
 *   1. 검색(Retrieval): 지금 순위 첫 쪽 + 요청 문장과 비슷한 코스(임베딩, 같은 반경 안)를 합쳐 후보 최대 MAX_CANDIDATES 개
 *   2. 생성(Generation): Ollama 에게 후보 번호·실제 정보만 주고 최대 3개를 고르게 한다 (AiGate: 한 번에 하나, 한도)
 *   3. 검수: 후보에 없는 번호는 버리고, 이유에 다른 후보의 장소 이름이나 지어낸 가격·시간이 있으면 이유만 뺀다
 *   4. 바쁘거나 실패하면 순위 위에서 3개 (ai=false). "다시 추천"은 본 코스를 exclude 로 넘겨 다른 후보에서 고른다
 * 같은 요청은 하루 캐시하고, 캐시 키에는 문장 대신 해시를 쓴다(문장을 저장하지 않음). 로그에도 문장을 남기지 않는다.
 */
@Service
public class AiRecommendService {

    private static final Logger log = LoggerFactory.getLogger(AiRecommendService.class);

    /** 후보는 순위 위쪽만 (작은 모델이 "앞 번호 우선"을 잘 안 지켜서, 무엇을 골라도 인기 있을 법한 코스가 되게) */
    static final int MAX_CANDIDATES = 12;
    static final int FROM_RANKING = 8;
    static final int MAX_PICKS = 3;
    static final int REASON_MAX = 60;
    static final String BUSY = "지금은 AI 가 바빠서 순위 위에서 골랐어요. 잠시 뒤에 다시 눌러 보세요.";
    static final String UNUSABLE = "AI 답을 쓸 수 없어서 순위 위에서 골랐어요. 다시 추천을 눌러 보세요.";
    /** 후보에 없는 가격·시간·거리 ("2만원", "10시", "300m") */
    private static final Pattern INVENTED = Pattern.compile("\\d[\\d,.]*\\s*(천|만)?\\s*(원|시|분|%|km|m\\b)");

    private final CourseQueryService courses;
    private final CourseEmbeddingService embeddings;
    private final KeywordService keywords;
    private final OllamaClient ollama;
    private final AiGate gate;
    private final JsonCache cache;

    public AiRecommendService(CourseQueryService courses, CourseEmbeddingService embeddings, KeywordService keywords,
                              OllamaClient ollama, AiGate gate, JsonCache cache) {
        this.courses = courses;
        this.embeddings = embeddings;
        this.keywords = keywords;
        this.ollama = ollama;
        this.gate = gate;
        this.cache = cache;
    }

    /** 캐시에 두는 결과 (settled: AI 가 답했거나 AI 가 꺼져 있음 → 하루, 바빴으면 5분) */
    record Cached(AiRecommendResponse response, boolean settled) {
    }

    /** 후보 하나 (AI 에게 보여줄 실제 정보) */
    record Candidate(long id, String title, String sigungu, Integer minutes, List<String> steps, List<String> stepNames,
                     List<String> keywordNames, boolean inRanking, String area) {

        Candidate(long id, String title, String sigungu, Integer minutes, List<String> steps, List<String> stepNames,
                  List<String> keywordNames, boolean inRanking) {
            this(id, title, sigungu, minutes, steps, stepNames, keywordNames, inRanking, sigungu);
        }
    }

    public AiRecommendResponse recommend(AiRecommendRequest body, String ip) {
        String q = AiParseService.clean(body == null ? null : body.q());
        // 지점 반경(lat 있음) 또는 시도 전체·전국(lat 없이 sido·scene)
        boolean region = body.lat() == null && ((body.sido() != null && !body.sido().isEmpty()) || body.scene() != null);
        CourseSearchRequest search = region ? null : courses.normalize(body.lat(), body.lng(), body.radius(), body.kw(), 0, body.scene(), body.food(), body.shop());
        CourseQueryService.RegionRequest regionSearch = region ? courses.normalizeRegion(body.sido(), body.scene(), body.kw(), 0, body.food()) : null;
        Set<Long> exclude = body.exclude() == null ? Set.of() : body.exclude().stream().limit(60).collect(Collectors.toSet());
        Shop shop = region ? null : Shop.of(body.shopName(), body.shopCategory());
        String key = "ai-rec:" + hash(q) + ":" + (region ? regionSearch.cacheKey() : search.cacheKey()) + ":"
                + exclude.stream().sorted().map(String::valueOf).collect(Collectors.joining(","))
                + (shop == null ? "" : ":with=" + hash(shop.name() + "|" + shop.category()));
        return cache.getOrLoad(key, (Cached c) -> c.settled() ? Duration.ofDays(1) : Duration.ofMinutes(5),
                new TypeReference<Cached>() { }, () -> {
                    AiRecommendResponse r = load(q, search, regionSearch, exclude, shop, ip);
                    return new Cached(r, r.ai() || !ollama.enabled());
                }).response();
    }

    /**
     * 가게 이름으로 찾은 가게 (AI 검색 "오리재"): 후보 코스마다 같은 종류 단계를 이 가게로 바꿔 보여주고 고르게 한다
     * (화면의 "넣어 보기"와 같은 규칙: 카페 → 카페 단계, 식당·술집 → 식당 단계, 그 밖에는 첫 장소 다음에 끼움)
     */
    record Shop(String name, String category) {

        static Shop of(String name, String category) {
            String n = name == null ? "" : name.replaceAll("[\\p{Cntrl}]", "").strip().replaceAll("\\s+", " ");
            if (n.length() < 2 || n.length() > 30) {
                return null;
            }
            String c = Set.of("cafe", "restaurant", "bar", "shop", "activity").contains(category) ? category : "shop";
            return new Shop(n, c);
        }

        String label() {
            return AiRecommendService.label(category);
        }
    }

    /** 후보에 가게를 넣는다. roles: 그 코스의 단계 역할(spot·cafe·meal) 순서대로. 이미 든 코스는 그대로 */
    static Candidate withShop(Candidate c, List<String> roles, Shop shop) {
        if (c.stepNames().contains(shop.name())) {
            return c;
        }
        String target = "cafe".equals(shop.category()) ? "cafe" : "restaurant".equals(shop.category()) || "bar".equals(shop.category()) ? "meal" : null;
        int at = target == null ? -1 : roles.indexOf(target);
        List<String> steps = new ArrayList<>(c.steps());
        List<String> names = new ArrayList<>(c.stepNames());
        String step = shop.name() + "(" + shop.label() + ")";
        if (at >= 0 && at < steps.size()) {
            steps.set(at, step);
            names.set(at, shop.name());
        } else {
            int meal = roles.indexOf("meal");
            int index = "cafe".equals(target) ? (meal >= 0 ? meal : steps.size()) : "meal".equals(target) ? steps.size() : Math.min(1, steps.size());
            steps.add(index, step);
            names.add(index, shop.name());
        }
        return new Candidate(c.id(), c.title(), c.sigungu(), c.minutes(), List.copyOf(steps), List.copyOf(names), c.keywordNames(), c.inRanking(), c.area());
    }

    private AiRecommendResponse load(String q, CourseSearchRequest search, CourseQueryService.RegionRequest regionSearch,
                                     Set<Long> exclude, Shop shop, String ip) {
        Map<String, String> names = keywords.all().stream().collect(Collectors.toMap(KeywordResponse::code, KeywordResponse::name, (a, b) -> a));
        CourseSearchResponse ranked = search != null ? courses.search(search) : courses.searchRegion(regionSearch);
        List<Candidate> ranking = ranked.courses().stream().filter(c -> !exclude.contains(c.id())).map(c -> fromCard(c, names)).toList();
        // 단계 역할 (가게를 넣을 자리를 찾으려고)
        Map<Long, List<String>> roles = new java.util.HashMap<>();
        ranked.courses().forEach(c -> roles.put(c.id(), c.steps().stream().map(s -> s.role()).toList()));

        // 요청 문장과 비슷한 코스 (지금 순위 첫 쪽에 없는 것만)
        List<Candidate> similar = new ArrayList<>();
        // 비슷한 코스는 지점 반경일 때만 (시도 전체·전국이면 풍경·시도 조건을 지키려고 순위 후보만 쓴다)
        if (search != null && embeddings.ready()) {
            List<float[]> qv = ollama.embed(List.of(q));
            if (!qv.isEmpty()) {
                Set<Long> skip = new LinkedHashSet<>(exclude);
                ranking.forEach(c -> skip.add(c.id()));
                for (long id : embeddings.similar(qv.get(0), search.lat(), search.lng(), ranked.radius(), skip, MAX_CANDIDATES - FROM_RANKING)) {
                    try {
                        CourseDetailResponse d = courses.detail(id);
                        roles.put(id, d.steps().stream().map(s -> s.role()).toList());
                        similar.add(fromDetail(d, names));
                    } catch (ApiException e) {
                        // 그새 숨긴 코스
                    }
                }
            }
        }
        List<Candidate> picked = new ArrayList<>(ranking.stream().limit(FROM_RANKING).toList());
        picked.addAll(similar);
        ranking.stream().skip(FROM_RANKING).limit(Math.max(0, MAX_CANDIDATES - picked.size())).forEach(picked::add);
        // 가게 이름으로 찾았으면 후보마다 그 가게를 넣은 코스로 (목록에 그 가게가 든 코스가 없어도 그 가게로 다니는 코스를 고르게)
        Set<Long> shopped = new LinkedHashSet<>();
        List<Candidate> candidates = shop == null ? picked : picked.stream().map(c -> {
            Candidate s = withShop(c, roles.getOrDefault(c.id(), List.of()), shop);
            if (s != c) {
                shopped.add(c.id());
            }
            return s;
        }).toList();
        if (candidates.isEmpty()) {
            return new AiRecommendResponse(false, "이 근처에서 더 고를 코스가 없어요. 지도를 넓히거나 옮겨 주세요.", 0, List.of());
        }

        // 너무 자주 누르면 AiGate 가 429 를 던져 그대로 알린다
        JsonNode answer = gate.call(ip, () -> ollama.generateJson(prompt(q, candidates, shop), 220));
        List<AiRecommendResponse.Pick> picks = (answer == null ? List.<AiRecommendResponse.Pick>of() : accept(answer, candidates)).stream()
                .map(p -> p.withShop(shopped.contains(p.courseId()))).toList();
        // 문장은 남기지 않고 답의 모양만 (바쁨과 쓸 수 없는 답을 나눠 보려고)
        log.info("AI 추천: 후보 {}개(비슷한 코스 {}), {}", candidates.size(), similar.size(),
                answer == null ? "바빠서 순위로" : picks.isEmpty() ? "쓸 수 없는 답(키 " + fieldNames(answer) + ", picks " + answer.path("picks").size() + "개)이라 순위로"
                        : "AI " + picks.size() + "개");
        if (picks.isEmpty()) {
            List<AiRecommendResponse.Pick> top = candidates.stream().limit(MAX_PICKS).map(c -> pick(c, null).withShop(shopped.contains(c.id()))).toList();
            return new AiRecommendResponse(false, answer == null ? BUSY : UNUSABLE, candidates.size(), top);
        }
        return new AiRecommendResponse(true, null, candidates.size(), picks);
    }

    private static String fieldNames(JsonNode node) {
        List<String> keys = new ArrayList<>();
        node.propertyNames().forEach(keys::add);
        return String.join(",", keys);
    }

    /** AI 답 검수: 후보 번호만, 겹치지 않게 3개까지. 이유는 다른 후보의 장소 이름·지어낸 가격·시간이 있으면 뺀다 */
    static List<AiRecommendResponse.Pick> accept(JsonNode answer, List<Candidate> candidates) {
        List<AiRecommendResponse.Pick> out = new ArrayList<>();
        Set<Integer> used = new LinkedHashSet<>();
        for (JsonNode p : answer.path("picks")) {
            // 작은 모델이 {"n":1,…} 대신 번호만([10, 19, 20]) 줄 때도 있다 (그때는 이유 없이)
            int n = p.isNumber() || p.isString() ? p.asInt(0) : p.path("n").asInt(0);
            if (n < 1 || n > candidates.size() || !used.add(n) || out.size() >= MAX_PICKS) {
                continue;
            }
            Candidate c = candidates.get(n - 1);
            out.add(pick(c, p.isObject() ? checkReason(p.path("reason").asString(""), c, candidates) : null));
        }
        // 화면에는 순위(인기) 순서로: 후보 번호가 앞일수록 순위가 높다
        List<Long> order = candidates.stream().map(Candidate::id).toList();
        out.sort(java.util.Comparator.comparingInt(x -> order.indexOf(x.courseId())));
        return out;
    }

    static String checkReason(String raw, Candidate mine, List<Candidate> all) {
        String reason = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (reason.isEmpty() || INVENTED.matcher(reason).find()) {
            return null;
        }
        // 다른 코스의 장소를 이 코스 이유로 쓴 경우: 장소 이름이 통째로 있거나, 이유의 낱말(4자 이상, 조사를 뗀)이
        // 다른 후보 장소 이름의 일부인데 이 코스에는 없을 때 ("송도해상케이블카에서" ↔ 다른 후보의 "송도해상케이블카(스카이)")
        String mineText = compact(mine.title() + String.join("", mine.stepNames()));
        List<String> words = java.util.Arrays.stream(reason.split("[\s,.!?·]+"))
                .map(w -> compact(w).replaceAll("(에서|에서는|으로|에는|과|와|을|를|이|가|은|는|의|도|로|에)$", ""))
                .filter(w -> w.length() >= 4).toList();
        for (Candidate other : all) {
            if (other.id() == mine.id()) {
                continue;
            }
            for (String name : other.stepNames()) {
                if (name.length() >= 3 && reason.contains(name) && !mine.stepNames().contains(name)) {
                    return null;
                }
                String otherName = compact(name);
                for (String w : words) {
                    if (otherName.contains(w) && !mineText.contains(w)) {
                        return null;
                    }
                }
            }
        }
        return reason.length() > REASON_MAX ? reason.substring(0, REASON_MAX - 1) + "…" : reason;
    }

    /** 띄어쓰기·괄호·기호를 뺀 글자 (이름 비교용) */
    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("[^0-9A-Za-z가-힣]", "");
    }

    private static AiRecommendResponse.Pick pick(Candidate c, String reason) {
        return new AiRecommendResponse.Pick(c.id(), c.title(), c.sigungu(), c.minutes(), c.stepNames(), reason, c.inRanking());
    }

    static Candidate fromCard(CourseCard c, Map<String, String> names) {
        List<String> steps = c.steps().stream().map(s -> s.name() + "(" + label(s.category()) + ")").toList();
        // 시도 전체·전국 후보는 어느 시도인지도 알려 준다 ("부산 해운대구")
        String area = String.join(" ", java.util.stream.Stream.of(c.sido(), c.sigungu()).filter(v -> v != null && !v.isBlank()).toList());
        return new Candidate(c.id(), c.title(), c.sigungu(), c.totalMinutes(), steps, c.steps().stream().map(s -> s.name()).toList(),
                c.keywords().stream().map(k -> names.getOrDefault(k, k)).toList(), true, area);
    }

    static Candidate fromDetail(CourseDetailResponse d, Map<String, String> names) {
        List<String> steps = d.steps().stream().map(s -> {
            String detail = s.place().detailCategory();
            String kind = detail != null && !detail.isBlank() && !detail.matches("[A-Z]{2}\\d.*") ? detail : label(s.place().category());
            return s.place().name() + "(" + kind + ")";
        }).toList();
        return new Candidate(d.id(), d.title(), d.sigungu(), d.totalMinutes(), steps, d.steps().stream().map(s -> s.place().name()).toList(),
                d.keywords().stream().map(k -> names.getOrDefault(k, k)).toList(), false);
    }

    static String label(String category) {
        return switch (category == null ? "" : category) {
            case "attraction" -> "관광명소";
            case "culture" -> "문화시설";
            case "activity" -> "놀거리";
            case "shop" -> "소품샵";
            case "cafe" -> "카페";
            case "restaurant" -> "식당";
            case "bar" -> "술집";
            default -> category;
        };
    }

    static String prompt(String q, List<Candidate> candidates) {
        return prompt(q, candidates, null);
    }

    /** shop: 가게 이름으로 찾았으면 그 가게 (후보에 넣어 두었다고 알려 이유에 써도 되게) */
    static String prompt(String q, List<Candidate> candidates, Shop shop) {
        StringBuilder sb = new StringBuilder("""
                너는 데이트 코스를 골라 주는 도우미다. 아래 후보 코스 중에서만 요청에 맞는 코스를 최대 3개 골라라.
                후보는 인기·품질이 높은 순서로 번호가 매겨져 있다(앞 번호일수록 많은 사람이 좋아할 만한 코스).
                규칙:
                - 요청과 맞는 정도가 비슷하면 앞 번호를 고른다. 요청에 조건이 거의 없으면 앞 번호에서 고른다.
                - 요청한 사람이 누구인지(첫 데이트, 오래 만난 커플, 나이 등) 추측하지 않는다. 이유에도 쓰지 않는다.
                - 아래 후보에 있는 코스만 고른다. 새 코스를 만들지 않는다.
                - 코스마다 n(후보 번호)과 reason(이유)을 함께 쓴다. 이유는 그 후보에 적힌 장소·업종·키워드만 근거로 한국어 한 문장, 40자 안.
                - 가격, 영업시간, 리뷰, 거리처럼 후보에 없는 사실은 지어내지 않는다.
                답 예시(이 모양 그대로): {"picks":[{"n":1,"reason":"해변 산책 뒤 바다 앞 카페로 이어져요"},{"n":2,"reason":"전망대에서 노을을 보고 식사해요"}]}
                """);
        if (shop != null) {
            sb.append("요청한 가게: ").append(shop.name()).append("(").append(shop.label()).append(")")
                    .append(". 아래 후보 코스에는 이 가게를 이미 넣어 두었다. 이 가게와 함께 다니기 좋은 코스를 고르고, 이유에 이 가게 이름을 써도 된다.\n");
        }
        sb.append("요청: ").append(q).append("\n후보:\n");
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            sb.append(i + 1).append(". ").append(c.title())
                    .append(" | ").append(c.area() == null ? "" : c.area())
                    .append(" | ").append(String.join(" → ", c.steps()));
            if (!c.keywordNames().isEmpty()) {
                sb.append(" | 키워드: ").append(String.join(", ", c.keywordNames()));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    static String hash(String text) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 20);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
