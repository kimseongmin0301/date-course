package com.datecourse.domain.ai.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "말로 찾기"의 규칙 부분. LLM 보다 먼저, 확실한 것만 알아듣는다 (빠르고 틀리지 않게).
 *   - 실내외: 비·눈·추위·더위 → 실내, 야외·피크닉 → 야외
 *   - 예산(2인): "5만원" → 3~7만원, 저렴·가성비 → 3만원 이하, 고급·플렉스 → 7만원 이상
 *   - 분위기·활동: 비슷한 말 사전 (조용·한적·차분 → 조용 등)
 *   - 지역 후보: 조사(에서·근처…)를 뗀 말 중 동·역·구·시·길로 끝나는 말 먼저, 그다음 나머지 말 (실제 지역인지는 지역 검색으로 확인)
 */
public final class QueryRules {

    private QueryRules() {
    }

    public record Parsed(Set<String> keywords, String setting, String budget, List<String> areaCandidates, Set<String> leftover) {

        /** 규칙만으로 지역·키워드를 다 알아들었는지 (못 알아들은 말이 남으면 LLM 에 물어본다) */
        public boolean complete() {
            return leftover.isEmpty();
        }
    }

    private static final Pattern INDOOR = Pattern.compile("비\\s*(오|와|올|내리)|비가|눈\\s*(오|와|올)|눈이|추운|추워|추울|더운|더워|더울|실내|장마");
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
            "오는", "오면", "가는", "갈만한", "하기", "좋을", "어디", "어디가", "뭐", "할까", "가자", "놀", "놀기", "먹고", "보는", "원해", "원하는", "느낌");
    private static final Pattern AREA_SUFFIX = Pattern.compile(".+(동|역|구|군|시|읍|면|리|길|로|해변|해수욕장|공원|시장)$");

    public static Parsed parse(String query) {
        String q = query == null ? "" : query.strip();
        String setting = INDOOR.matcher(q).find() ? "indoor" : OUTDOOR.matcher(q).find() ? "outdoor" : null;
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
            if (word.isEmpty() || word.matches("\\d+.*") || STOP.contains(word)) {
                continue;
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
        return new Parsed(keywords, setting, budget, areas.stream().distinct().limit(4).toList(), leftover);
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
