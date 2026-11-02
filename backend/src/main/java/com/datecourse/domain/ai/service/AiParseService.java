package com.datecourse.domain.ai.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.datecourse.domain.ai.client.OllamaClient;
import com.datecourse.domain.ai.dto.AiParseResponse;
import com.datecourse.domain.ai.exception.AiException;
import com.datecourse.domain.ai.util.QueryRules;
import com.datecourse.domain.keyword.dto.KeywordResponse;
import com.datecourse.domain.keyword.service.KeywordService;
import com.datecourse.domain.place.dto.LocationResult;
import com.datecourse.domain.place.service.LocationSearchService;
import com.datecourse.global.cache.JsonCache;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

/**
 * 말로 찾기 (실험 기능): "비 오는 날 성수동 조용한 데이트, 5만원" → 지역 + 키워드 칩.
 *   1. 규칙(QueryRules)이 실내외·예산·비슷한 말을 먼저 알아듣고, 지역은 낱말마다 우리 지역 데이터로 확인한다.
 *   2. 지역이나 분위기·활동을 못 알아들었을 때만 Ollama 에 묻는다 (AiGate: 한 번에 하나, 한도, 바쁘면 건너뜀).
 *   3. AI 답은 검수한다: 지역은 원래 문장에 그 글자가 있을 때만, 키워드는 정해진 17개 이름만. 규칙이 정한 실내외·예산은 AI 가 못 바꾼다.
 * 결과는 화면이 키워드 칩으로 보여주고 사람이 고친다. 같은 문장은 하루 캐시(AI 를 못 불렀으면 10분). 캐시 키는 문장 대신 해시.
 */
@Service
public class AiParseService {

    public static final int MIN = 2;
    public static final int MAX = 100;
    static final int MAX_KEYWORDS = 3;

    private final LocationSearchService locations;
    private final KeywordService keywords;
    private final OllamaClient ollama;
    private final AiGate gate;
    private final JsonCache cache;

    public AiParseService(LocationSearchService locations, KeywordService keywords, OllamaClient ollama, AiGate gate, JsonCache cache) {
        this.locations = locations;
        this.keywords = keywords;
        this.ollama = ollama;
        this.gate = gate;
        this.cache = cache;
    }

    record Cached(AiParseResponse response, boolean settled) {
    }

    public AiParseResponse parse(String query, String ip) {
        String q = clean(query);
        Cached cached = cache.getOrLoad("ai-parse:" + AiRecommendService.hash(q), (Cached c) -> c.settled() ? Duration.ofDays(1) : Duration.ofMinutes(10),
                new TypeReference<Cached>() { }, () -> load(q, ip));
        return cached.response();
    }

    static String clean(String query) {
        String q = query == null ? "" : String.join(" ", query.strip().split("\\s+"));
        if (q.length() < MIN || q.length() > MAX) {
            throw AiException.badQuery(MIN, MAX);
        }
        return q;
    }

    private Cached load(String q, String ip) {
        QueryRules.Parsed rules = QueryRules.parse(q);
        LocationResult area = null;
        for (String candidate : rules.areaCandidates()) {
            List<LocationResult> found = locations.searchLocal(candidate);
            if (!found.isEmpty()) {
                area = found.get(0);
                break;
            }
        }
        Set<String> moods = new LinkedHashSet<>(rules.keywords());
        String setting = rules.setting();
        String budget = rules.budget();
        boolean aiUsed = false;
        boolean settled = true;

        if (area == null || moods.isEmpty()) {
            JsonNode ai = null;
            try {
                ai = gate.call(ip, () -> ollama.generateJson(prompt(q), 160));
            } catch (AiException e) {
                // 너무 자주 눌렀으면 AI 없이 규칙 결과만
            }
            if (ai == null) {
                settled = !ollama.enabled();   // 바빠서 못 불렀으면 짧게만 캐시
            } else {
                Accepted accepted = accept(ai, q, keywordsByName());
                if (area == null && accepted.area() != null) {
                    List<LocationResult> found = locations.searchLocal(accepted.area());
                    if (found.isEmpty()) {
                        found = locations.search(accepted.area());
                    }
                    if (!found.isEmpty()) {
                        area = found.get(0);
                        aiUsed = true;
                    }
                }
                if (moods.isEmpty() && !accepted.moods().isEmpty()) {
                    moods.addAll(accepted.moods());
                    aiUsed = true;
                }
                if (setting == null && accepted.setting() != null) {
                    setting = accepted.setting();
                    aiUsed = true;
                }
                if (budget == null && accepted.budget() != null) {
                    budget = accepted.budget();
                    aiUsed = true;
                }
            }
        }

        List<String> codes = new ArrayList<>();
        if (setting != null) {
            codes.add(setting);
        }
        if (budget != null) {
            codes.add(budget);
        }
        for (String m : moods) {
            if (codes.size() < MAX_KEYWORDS) {
                codes.add(m);
            }
        }
        return new Cached(new AiParseResponse(area, codes, aiUsed, understood(area, codes)), settled);
    }

    private static final java.util.regex.Pattern SETTING_HINT = java.util.regex.Pattern.compile("실내|실외|야외|밖|안에서|날씨|비|눈|덥|더운|추운|춥|햇");
    private static final java.util.regex.Pattern BUDGET_HINT = java.util.regex.Pattern.compile("부담|저렴|싸|비싸|비싼|가격|예산|돈|만원|가성비|고급|럭셔리");

    /** AI 답에서 받아들인 것 */
    record Accepted(String area, List<String> moods, String setting, String budget) {
    }

    /** AI 답 검수: 지역은 원래 문장에 있는 글자만, 키워드·실내외·예산은 정해진 이름(또는 코드)만 */
    static Accepted accept(JsonNode ai, String query, Map<String, KeywordResponse> byName) {
        String area = ai.path("area").isString() ? ai.path("area").asString("").strip() : "";
        String compactQuery = query.replace(" ", "");
        if (area.length() < 2 || !compactQuery.contains(area.replace(" ", ""))) {
            area = null;
        }
        List<String> moods = new ArrayList<>();
        for (JsonNode k : ai.path("keywords")) {
            KeywordResponse kw = byName.get(k.asString("").replace(" ", ""));
            if (kw != null && ("mood".equals(kw.kind()) || "activity".equals(kw.kind())) && !moods.contains(kw.code()) && moods.size() < MAX_KEYWORDS) {
                moods.add(kw.code());
            }
        }
        // 실내외·예산은 문장에 그런 말이 있을 때만 받는다 (작은 모델이 "첫 데이트"에 야외를 지어낸 적이 있음)
        KeywordResponse setting = SETTING_HINT.matcher(query).find() ? byName.get(ai.path("setting").asString("").replace(" ", "")) : null;
        KeywordResponse budget = BUDGET_HINT.matcher(query).find() ? byName.get(ai.path("budget").asString("").replace(" ", "")) : null;
        return new Accepted(area, moods, setting != null && "setting".equals(setting.kind()) ? setting.code() : null,
                budget != null && "budget".equals(budget.kind()) ? budget.code() : null);
    }

    /** 이름("전시·문화", 띄어쓰기 없이)과 코드로 모두 찾게 */
    private Map<String, KeywordResponse> keywordsByName() {
        Map<String, KeywordResponse> map = new java.util.HashMap<>();
        for (KeywordResponse k : keywords.all()) {
            map.put(k.name().replace(" ", ""), k);
            map.put(k.code(), k);
            map.put(k.name().replace(" ", "").replace("·", ""), k);
        }
        return map;
    }

    String understood(LocationResult area, List<String> codes) {
        Map<String, String> names = keywords.all().stream().collect(Collectors.toMap(KeywordResponse::code, KeywordResponse::name, (a, b) -> a));
        List<String> parts = new ArrayList<>();
        parts.add(area == null ? "지금 지도에서" : area.name());
        codes.stream().map(c -> names.getOrDefault(c, c)).forEach(parts::add);
        return String.join(" · ", parts);
    }

    static String prompt(String q) {
        return """
                너는 데이트 장소 검색 조건을 뽑는 도우미다. 아래 요청에서 조건만 JSON 으로 뽑아라.
                요청에 없는 것은 null 이나 빈 배열로 두고, 추측하지 마라.
                키:
                - area: 요청에 적힌 지역·동네·역 이름을 요청에 적힌 그대로. 없으면 null
                - keywords: 다음 이름 중에서만 최대 3개: 조용, 로맨틱, 활기찬, 감성, 이색적, 산책, 전시·문화, 체험·공방, 쇼핑, 액티비티, 야경, 맛집 투어
                - setting: 실내, 야외 중 하나 또는 null (비·눈·추위·더위면 실내)
                - budget: 3만원 이하, 3~7만원, 7만원 이상 중 하나 또는 null (2인 기준)
                요청: """ + q;
    }
}
