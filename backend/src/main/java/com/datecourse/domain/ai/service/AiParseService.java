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
import com.datecourse.domain.course.util.Foods;
import com.datecourse.domain.course.util.Scenes;
import com.datecourse.domain.keyword.dto.KeywordResponse;
import com.datecourse.domain.keyword.service.KeywordService;
import com.datecourse.domain.place.dto.LocationResult;
import com.datecourse.domain.place.service.LocationSearchService;
import com.datecourse.global.cache.JsonCache;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

/**
 * AI 검색 (실험 기능): "비 오는 날 성수동 조용한 데이트, 5만원" → 지역 + 키워드 칩.
 *   1. 규칙(QueryRules)이 실내외·예산·비슷한 말·메뉴를 먼저 알아듣고, 지역은 낱말마다 우리 지역 데이터로 확인한다.
 *      없으면 앞의 두 낱말을 네이버 지역 검색으로 확인한다 (상권·건물 이름: "안양일번가").
 *   2. 지역이나 분위기·활동을 못 알아들었을 때만 Ollama 에 묻는다 (AiGate: 한 번에 하나, 한도, 바쁘면 건너뜀).
 *   3. AI 답은 검수한다: 지역은 원래 문장에 그 글자가 있을 때만, 키워드는 정해진 17개 이름만. 규칙이 정한 실내외·예산은 AI 가 못 바꾼다.
 * 결과는 화면이 키워드 칩으로 보여주고 사람이 고친다. 같은 문장은 하루 캐시(AI 를 못 불렀으면 10분). 캐시 키는 문장 대신 해시.
 */
@Service
public class AiParseService {

    public static final int MIN = 2;
    public static final int MAX = 100;
    static final int MAX_KEYWORDS = 3;
    /** 네이버 지역 검색으로 확인해 볼 낱말 수 */
    static final int NAMED_TRIES = 2;

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
        List<String> sidos = new ArrayList<>(rules.sidos());
        for (String candidate : rules.areaCandidates()) {
            area = pickArea(candidate, locations.searchLocal(candidate), sidos);
            if (area != null) {
                break;
            }
        }
        // 우리 지역 데이터에 없는 상권·건물 이름("안양일번가", "더현대서울")은 네이버 지역 검색으로.
        // 세 글자 이상 앞의 두 낱말만 (하루 한도를 아끼고, "놀거리" 같은 짧은 말이 가게 이름에 걸려 엉뚱한 곳으로 가지 않게)
        if (area == null) {
            for (String candidate : rules.areaCandidates().stream().filter(w -> w.length() >= 3).limit(NAMED_TRIES).toList()) {
                area = pickArea(candidate, locations.searchNamed(candidate), sidos);
                if (area != null) {
                    break;
                }
            }
        }
        String food = rules.food();
        String scene = rules.scene();
        Set<String> moods = new LinkedHashSet<>(rules.keywords());
        String setting = rules.setting();
        String budget = rules.budget();
        boolean aiUsed = false;
        boolean settled = true;

        // 지역(지점·시도·풍경 중 하나)이나 분위기(풍경이 있으면 그것으로 충분)를 못 알아들었을 때만 AI 에게
        boolean needArea = area == null && sidos.isEmpty() && scene == null;
        if (needArea || (moods.isEmpty() && scene == null && food == null)) {
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
                String aiSido = accepted.area() == null ? null : QueryRules.sidoOf(accepted.area().replace(" ", ""));
                if (aiSido != null && sidos.isEmpty() && area == null) {
                    sidos.add(aiSido);   // AI 가 "부산"을 지역으로 짚어도 지점(부산진구)이 아니라 시도 전체
                    aiUsed = true;
                } else if (area == null && accepted.area() != null) {
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
        return new Cached(new AiParseResponse(area, codes, aiUsed, understood(area, sidos, scene, rules.rainy(), food, codes),
                List.copyOf(sidos), scene, rules.rainy(), food), settled);
    }

    private static final java.util.regex.Pattern SETTING_HINT = java.util.regex.Pattern.compile("실내|실외|야외|밖|안에서|날씨|비|눈|덥|더운|추운|춥|햇");
    private static final java.util.regex.Pattern BUDGET_HINT = java.util.regex.Pattern.compile("부담|저렴|싸|비싸|비싼|가격|예산|돈|만원|가성비|고급|럭셔리");

    /**
     * 낱말로 찾은 지역 중 받아들일 것. 명소(place)는 이름이 낱말과 같거나 낱말(3자 이상)로 시작할 때만
     * ("카페" → "카페도자기마을"처럼 이름에 들어 있기만 한 곳을 지점으로 잡지 않게), 시도를 알아들었으면 그 시도 안의 것만
     */
    static LocationResult pickArea(String word, List<LocationResult> found, List<String> sidos) {
        for (LocationResult r : found) {
            String name = r.name().replace(" ", "");
            boolean placeOk = !"place".equals(r.kind()) || name.equals(word) || (word.length() >= 3 && name.startsWith(word));
            boolean inSido = sidos.isEmpty() || sidos.stream().anyMatch(sd -> {
                String text = r.name() + " " + (r.detail() == null ? "" : r.detail());
                return text.contains(sd) || text.contains(sd.substring(0, 2));
            });
            if (placeOk && inSido) {
                return r;
            }
        }
        return null;
    }

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

    String understood(LocationResult area, List<String> sidos, String scene, boolean rainy, String food, List<String> codes) {
        Map<String, String> names = keywords.all().stream().collect(Collectors.toMap(KeywordResponse::code, KeywordResponse::name, (a, b) -> a));
        List<String> parts = new ArrayList<>();
        if (area != null) {
            parts.add(area.name());
        } else if (!sidos.isEmpty()) {
            parts.add(sidos.stream().map(AiParseService::shortSido).collect(Collectors.joining("·")));
        } else {
            parts.add(scene != null ? "전국" : "지금 지도에서");
        }
        Scenes.Scene s = Scenes.of(scene);
        if (s != null) {
            parts.add(s.name());
        }
        if (rainy) {
            parts.add("비 오는 날");
        }
        Foods.Food f = Foods.of(food);
        if (f != null) {
            parts.add(f.name());
        }
        codes.stream().map(c -> names.getOrDefault(c, c)).forEach(parts::add);
        return String.join(" · ", parts);
    }

    /** "부산광역시" → 부산, "전남광주통합특별시" → 전남·광주 (화면에 짧게) */
    static String shortSido(String sido) {
        return switch (sido) {
            case "전남광주통합특별시" -> "전남·광주";
            case "전북특별자치도" -> "전북";
            case "강원특별자치도" -> "강원";
            case "제주특별자치도" -> "제주";
            case "세종특별자치시" -> "세종";
            case "충청북도" -> "충북";
            case "충청남도" -> "충남";
            case "경상북도" -> "경북";
            case "경상남도" -> "경남";
            case "경기도" -> "경기";
            default -> sido.replaceAll("(특별시|광역시)$", "");
        };
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
