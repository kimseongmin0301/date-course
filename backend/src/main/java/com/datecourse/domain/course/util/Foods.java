package com.datecourse.domain.course.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 메뉴로 코스 찾기 (AI 검색의 "햄버거", "초밥", "고기"). 코스의 식당·카페 단계 장소 이름이나 업종이 이 정규식(PostgreSQL ~)에 맞으면
 * 그 메뉴가 든 코스다. 메뉴는 거르지 않고 순위만 올린다: 자동 코스는 체인·간단한 음식점을 덜 고르게 만들어서
 * 햄버거집이 든 코스가 전국 54개뿐이었다(안양은 0개). 맞는 코스가 없으면 화면이 알리고 코스에 그 가게를 넣게 한다.
 *   words: 사람이 쓰는 말 (낱말이 이 말로 시작하면, 한 글자는 낱말과 같을 때만), search: 코스 상세에서 가게를 찾을 때 쓸 말
 */
public final class Foods {

    private Foods() {
    }

    public record Food(String code, String name, String search, List<String> words, String regex) {
    }

    public static final Map<String, Food> ALL = new LinkedHashMap<>();

    static {
        add(new Food("burger", "햄버거", "버거", List.of("햄버거", "버거", "수제버거"), "버거"));
        add(new Food("pizza", "피자", "피자", List.of("피자"), "피자"));
        add(new Food("pasta", "파스타·스테이크", "파스타", List.of("파스타", "스테이크", "양식", "이탈리안"), "파스타|스테이크|경양식|이탈리|양식"));
        add(new Food("sushi", "초밥·회", "초밥", List.of("초밥", "스시", "오마카세", "사시미", "회", "횟집"), "초밥|스시|오마카세|사시미|회/|횟집"));
        add(new Food("meat", "고기", "고기", List.of("고기", "고깃집", "삼겹살", "갈비", "한우", "소고기", "돼지고기", "바베큐"), "고기|갈비|삼겹|한우|구이/찜"));
        add(new Food("chicken", "치킨", "치킨", List.of("치킨", "통닭"), "치킨|통닭"));
        add(new Food("gopchang", "곱창", "곱창", List.of("곱창", "막창"), "곱창|막창"));
        add(new Food("seafood", "해산물", "해물", List.of("해산물", "해물", "조개", "장어", "대게"), "해산물|해물|조개|장어|대게|횟집"));
        add(new Food("noodle", "면 요리", "국수", List.of("국수", "칼국수", "냉면", "라멘", "우동", "소바"), "국수|냉면|라멘|우동|소바|면 요리"));
        add(new Food("asian", "아시아 음식", "쌀국수", List.of("쌀국수", "베트남", "태국", "마라탕", "훠궈", "동남아"), "베트남|쌀국수|태국|동남아|마라|훠궈"));
        add(new Food("chinese", "중식", "중식", List.of("중식", "중국집", "짜장", "짬뽕", "딤섬"), "중국|중식|짜장|짬뽕|딤섬"));
        add(new Food("korean", "한식", "한정식", List.of("한식", "한정식", "백반"), "한정식|백반|한식"));
        add(new Food("katsu", "돈가스·덮밥", "돈가스", List.of("돈가스", "돈까스", "카레", "덮밥"), "돈가스|돈까스|카레|덮밥"));
        add(new Food("snack", "분식", "분식", List.of("분식", "떡볶이", "김밥"), "분식|떡볶이|김밥"));
        add(new Food("bakery", "빵·디저트", "빵", List.of("빵", "빵집", "베이커리", "디저트", "케이크", "도넛"), "빵|도넛|디저트|케이크|베이커리"));
        add(new Food("brunch", "브런치", "브런치", List.of("브런치", "샌드위치", "샐러드"), "브런치|샌드위치|샐러드|토스트"));
    }

    private static void add(Food f) {
        ALL.put(f.code(), f);
    }

    /** 코드 → 메뉴 (모르는 코드나 null 은 null) */
    public static Food of(String code) {
        return code == null ? null : ALL.get(code);
    }

    /** 조사를 뗀 낱말이 메뉴면 그 메뉴 ("햄버거집" → 햄버거, "회" → 초밥·회, "회사"는 아님) */
    public static Food ofWord(String word) {
        for (Food f : ALL.values()) {
            for (String w : f.words()) {
                if (w.length() == 1 ? word.equals(w) || word.equals(w + "집") : word.startsWith(w)) {
                    return f;
                }
            }
        }
        return null;
    }
}
