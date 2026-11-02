package com.datecourse.domain.ai.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.datecourse.domain.collector.util.Regions;
import com.datecourse.domain.course.util.Foods;

/**
 * "AI 검색"의 규칙 부분. LLM 보다 먼저, 확실한 것만 알아듣는다 (빠르고 틀리지 않게).
 *   - 실내외: 비·눈·추위·더위 → 실내, 야외·피크닉 → 야외
 *   - 예산(2인): "5만원" → 3~7만원, 저렴·가성비 → 3만원 이하, 고급·플렉스 → 7만원 이상
 *   - 분위기·활동: 비슷한 말 사전 (조용·한적·차분 → 조용 등)
 *   - 지역 후보: 조사(에서·근처…)를 뗀 말 중 동·역·구·시·길로 끝나는 말 먼저, 그다음 나머지 말 (실제 지역인지는 지역 검색으로 확인)
 *   - 풍경: 바다·바닷가·해변 → 바다, 산·숲·오름 → 산·숲, 강·호수·계곡 → 강·호수 (코스 단계 장소 이름으로 찾음)
 *   - 시도: "부산", "제주도", "경상남도"는 지점이 아니라 그 시도 전체 (예전엔 "부산"이 같은 글자로 시작하는 부산진구=서면으로 잡혔음).
 *     "전라도"·"경상도"·"충청도"는 시도 둘, "서해(안)"·"남해안"·"동해안"은 그 바다를 낀 시도들 + 바다
 *   - 비·눈 + 풍경: "비 오는 날 바다"는 실내로 바꾸지 않고 비 오는 날 그 풍경을 원하는 것으로 본다
 *   - 메뉴: 햄버거·초밥·고기… (Foods) → 그 메뉴 식당이 든 코스를 위로. 메뉴 말은 지역 후보가 아니다 ("안양일번가 햄버거")
 */
public final class QueryRules {

    private QueryRules() {
    }

    /** scene: 풍경 코드(없으면 null), sidos: 시도 범위(비면 없음), rainy: 비·눈 오는 날, food: 메뉴 코드(없으면 null) */
    public record Parsed(Set<String> keywords, String setting, String budget, List<String> areaCandidates, Set<String> leftover,
                         String scene, List<String> sidos, boolean rainy, String food) {

        /** 규칙만으로 지역·키워드를 다 알아들었는지 (못 알아들은 말이 남으면 LLM 에 물어본다) */
        public boolean complete() {
            return leftover.isEmpty();
        }
    }

    private static final Pattern INDOOR = Pattern.compile("비\\s*(오|와|올|내리)|비가|눈\\s*(오|와|올)|눈이|추운|추워|추울|더운|더워|더울|실내|장마");
    /** 비·눈 (풍경을 원하면 실내로 바꾸지 않는다) */
    private static final Pattern RAIN = Pattern.compile("비\\s*(오|와|올|내리)|비가|눈\\s*(오|와|올)|눈이|장마");
    /** 날씨가 아니라 실내를 바라는 말 (풍경이 있어도 실내) */
    private static final Pattern INDOOR_WISH = Pattern.compile("실내|추운|추워|추울|더운|더워|더울");

    /** 풍경 코드 → 말 안에 들어 있으면 되는 말 / 낱말이 이것과 같아야 하는 말(조사를 뗀 뒤, "산책"이 산이 되지 않게) */
    static final Map<String, List<String>> SCENE_CONTAINS = new LinkedHashMap<>();
    static final Map<String, Set<String>> SCENE_EXACT = new LinkedHashMap<>();

    static {
        SCENE_CONTAINS.put("sea", List.of("바다", "바닷", "해변", "해수욕장", "해안", "오션", "파도", "비치"));
        SCENE_EXACT.put("sea", Set.of());
        SCENE_CONTAINS.put("mountain", List.of("등산", "오름", "산행", "수목원", "휴양림", "산림", "숲길"));
        SCENE_EXACT.put("mountain", Set.of("산", "숲", "산에", "산으로", "숲에"));
        SCENE_CONTAINS.put("water", List.of("강변", "강가", "한강", "호수", "호숫가", "계곡", "폭포", "저수지"));
        SCENE_EXACT.put("water", Set.of("강", "강에"));
    }

    /** 시도 여럿을 한 번에 부르는 말. 바다가 붙은 말은 풍경도 바다 */
    record Group(List<String> sidos, boolean sea) {
    }

    static final Map<String, Group> GROUPS = new LinkedHashMap<>();

    static {
        GROUPS.put("전라도", new Group(List.of("전북특별자치도", "전남광주통합특별시"), false));
        GROUPS.put("전라", new Group(List.of("전북특별자치도", "전남광주통합특별시"), false));
        GROUPS.put("경상도", new Group(List.of("경상북도", "경상남도"), false));
        GROUPS.put("충청도", new Group(List.of("충청북도", "충청남도"), false));
        GROUPS.put("서해", new Group(List.of("인천광역시", "경기도", "충청남도", "전북특별자치도", "전남광주통합특별시"), true));
        GROUPS.put("서해안", new Group(List.of("인천광역시", "경기도", "충청남도", "전북특별자치도", "전남광주통합특별시"), true));
        GROUPS.put("남해안", new Group(List.of("부산광역시", "경상남도", "전남광주통합특별시"), true));
        GROUPS.put("동해안", new Group(List.of("강원특별자치도", "경상북도", "울산광역시", "부산광역시"), true));
    }
    private static final Pattern OUTDOOR = Pattern.compile("야외|바깥|밖에서|피크닉|소풍");
    private static final Pattern MANWON = Pattern.compile("(\\d{1,3})\\s*만\\s*원?");
    private static final Pattern CHEAP = Pattern.compile("저렴|싸게|싼|가성비|부담\\s*없|알뜰");
    private static final Pattern PRICEY = Pattern.compile("비싼|고급|럭셔리|플렉스|호화");

    /** 키워드 코드 → 비슷한 말 (말 앞부분만 맞으면 됨: "조용한" → 조용) */
    static final Map<String, List<String>> SYNONYMS = new LinkedHashMap<>();

    static {
        SYNONYMS.put("quiet", List.of("조용", "한적", "차분", "대화", "아늑", "여유"));
        SYNONYMS.put("romantic", List.of("로맨틱", "낭만", "분위기", "기념일", "프러포즈", "프로포즈", "설레"));
        SYNONYMS.put("lively", List.of("활기", "신나", "북적", "힙한", "핫플", "핫한"));
        SYNONYMS.put("cozy", List.of("감성", "예쁜", "이쁜", "사진", "인스타"));
        SYNONYMS.put("unique", List.of("이색", "특별", "색다른", "독특", "새로운"));
        SYNONYMS.put("walk", List.of("산책", "걷", "공원", "둘레길"));
        SYNONYMS.put("exhibition", List.of("전시", "미술관", "박물관", "갤러리", "문화"));
        SYNONYMS.put("workshop", List.of("체험", "공방", "만들기", "원데이"));
        SYNONYMS.put("shopping", List.of("쇼핑", "소품", "편집숍", "편집샵"));   // "구경"은 관광 구경에도 써서 뺌
        SYNONYMS.put("play", List.of("액티비티", "방탈출", "볼링", "게임", "오락", "활동적"));
        SYNONYMS.put("night-view", List.of("야경", "노을", "밤바다", "밤에"));
        SYNONYMS.put("food-tour", List.of("맛집", "맛있", "먹방", "미식", "먹으러"));
    }

    private static final List<String> JOSA = List.of("에서의", "에서", "으로", "까지", "부터", "근처", "주변", "쪽에", "쪽", "에", "의", "로", "가서", "가면");
    private static final Set<String> STOP = Set.of("데이트", "코스", "추천", "추천해", "추천해줘", "하고", "싶어", "싶은", "싶다", "좋은", "좋아", "곳", "장소",
            "둘이", "커플", "연인", "여자친구", "남자친구", "애인", "주말", "토요일", "일요일", "평일", "오늘", "내일", "저녁", "점심", "낮", "밤", "오후", "오전",
            "정도", "쯤", "하는", "할", "만한", "갈", "가고", "같이", "함께", "찾아줘", "알려줘", "보고", "있는", "없는", "그리고", "또는", "날", "때", "비", "눈",
            "오는", "오면", "가는", "갈만한", "하기", "좋을", "어디", "어디가", "뭐", "할까", "가자", "놀", "놀기", "먹고", "보는", "원해", "원하는", "느낌",
            // 가게 종류는 지역 이름이 아니다 ("제주 바다 감성 카페"의 "카페"가 경기의 "카페도자기마을"로 잡혔음)
            "카페", "커피", "식당", "밥집", "음식점", "레스토랑", "술집", "디저트", "브런치", "빵집", "베이커리", "가게");
    private static final Pattern AREA_SUFFIX = Pattern.compile(".+(동|역|구|군|시|읍|면|리|길|로|해변|해수욕장|공원|시장)$");

    public static Parsed parse(String query) {
        String q = query == null ? "" : query.strip();
        String setting = INDOOR.matcher(q).find() ? "indoor" : OUTDOOR.matcher(q).find() ? "outdoor" : null;
        boolean rainy = RAIN.matcher(q).find();
        String scene = null;
        String food = null;
        List<String> sidos = new ArrayList<>();
        String budget = null;
        Matcher m = MANWON.matcher(q);
        if (m.find()) {
            int won = Integer.parseInt(m.group(1));
            budget = won <= 3 ? "budget-low" : won <= 7 ? "budget-mid" : "budget-high";
        } else if (CHEAP.matcher(q).find()) {
            budget = "budget-low";
        } else if (PRICEY.matcher(q).find()) {
            budget = "budget-high";
        }

        Set<String> keywords = new LinkedHashSet<>();
        List<String> suffixed = new ArrayList<>();
        List<String> others = new ArrayList<>();
        Set<String> leftover = new LinkedHashSet<>();
        for (String raw : q.split("[\\s,.!?·/]+")) {
            String word = stripJosa(raw.replaceAll("[^0-9A-Za-z가-힣]", ""));
            if (word.isEmpty() || word.matches("\\d+.*")) {
                continue;
            }
            Foods.Food foodOfWord = Foods.ofWord(word);
            if (foodOfWord != null) {
                if (food == null) {
                    food = foodOfWord.code();
                }
                continue;   // "빵", "디저트"처럼 STOP 에도 있는 말도 메뉴로
            }
            if (STOP.contains(word)) {
                continue;
            }
            String sceneOfWord = sceneOf(word);
            if (sceneOfWord != null && scene == null) {
                scene = sceneOfWord;
            }
            Group group = GROUPS.get(word);
            String sido = group == null ? sidoOf(word) : null;
            if (group != null || sido != null) {
                for (String s : group != null ? group.sidos() : List.of(sido)) {
                    if (!sidos.contains(s)) {
                        sidos.add(s);
                    }
                }
                if (group != null && group.sea() && scene == null) {
                    scene = "sea";
                }
                continue;
            }
            if (sceneOfWord != null && !AREA_SUFFIX.matcher(word).matches()) {
                continue;   // "바다", "바닷가"는 지역이 아니라 풍경 ("광안리해변"은 지역이기도 해서 아래로)
            }
            String code = keywordOf(word);
            if (code != null) {
                keywords.add(code);
            } else if (INDOOR.matcher(word).find() || OUTDOOR.matcher(word).find() || CHEAP.matcher(word).find() || PRICEY.matcher(word).find()) {
                // 실내외·예산으로 이미 알아들은 말
            } else if (word.length() >= 2 && AREA_SUFFIX.matcher(word).matches()) {
                suffixed.add(word);
            } else if (word.length() >= 2) {
                others.add(word);
                leftover.add(word);
            }
        }
        List<String> areas = new ArrayList<>(suffixed);
        areas.addAll(others);
        // 비·눈 오는 날 풍경을 원하면 실내로 바꾸지 않는다 ("실내", "추워서"처럼 실내를 바라는 말이 있으면 그대로 실내)
        if (scene != null && "indoor".equals(setting) && !INDOOR_WISH.matcher(q).find()) {
            setting = null;
        }
        return new Parsed(keywords, setting, budget, areas.stream().distinct().limit(4).toList(), leftover, scene, List.copyOf(sidos), rainy, food);
    }

    static String sceneOf(String word) {
        for (Map.Entry<String, List<String>> e : SCENE_CONTAINS.entrySet()) {
            if (SCENE_EXACT.get(e.getKey()).contains(word) || e.getValue().stream().anyMatch(word::contains)) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * 시도 이름이면 공식 이름. "부산", "부산시", "제주도", "경상남도", "강원도"는 시도, "부산진구", "제주시"는 시도가 아니라 지역
     */
    public static String sidoOf(String word) {
        String canonical = Regions.canonicalSido(word);
        if (canonical == null) {
            return null;
        }
        if (word.equals(canonical) || (word.endsWith("도") && word.length() <= 4)) {
            return canonical;
        }
        if (word.length() <= 3 && !(word.endsWith("시") && !canonical.endsWith("시")) && !word.endsWith("구") && !word.endsWith("군")) {
            return canonical;
        }
        return null;
    }

    static String keywordOf(String word) {
        for (Map.Entry<String, List<String>> e : SYNONYMS.entrySet()) {
            for (String s : e.getValue()) {
                if (word.startsWith(s) || (s.length() >= 2 && word.contains(s))) {
                    return e.getKey();
                }
            }
        }
        return null;
    }

    static String stripJosa(String word) {
        for (String j : JOSA) {
            if (word.length() > j.length() + 1 && word.endsWith(j)) {
                return word.substring(0, word.length() - j.length());
            }
        }
        return word;
    }
}
