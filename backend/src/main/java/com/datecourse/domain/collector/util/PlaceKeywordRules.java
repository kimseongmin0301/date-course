package com.datecourse.domain.collector.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.datecourse.domain.place.entity.PlaceCategory;

/**
 * 장소의 분류·업종·이름으로 키워드를 자동으로 고른다 (place_keyword.origin = auto).
 * 확실한 것만 붙인다: 모르면 안 붙이는 편이 낫다 (검색에서 엉뚱한 곳이 나오는 것보다 빠지는 게 덜 나쁨).
 *  - 업종 규칙: detail_category 가 이 말로 시작하면 (상가정보는 업종 이름, TourAPI 는 분류 코드 lclsSystm3)
 *  - 이름 규칙: 정리한 이름(PlaceNames.normalize)에 이 말이 들어 있으면 (루프탑, 공방 …)
 *  - 실내외(setting)·예산(budget)은 하나만: 더 자세한 규칙(긴 접두어) → 이름 규칙 순으로 덮어쓴다.
 *    실내외가 안 정해지면 관광명소는 야외, 나머지는 실내. 예산은 음식점·카페·술집에만 붙인다.
 */
public final class PlaceKeywordRules {

    private record Rule(String prefix, Set<String> tags, String setting, String budget) {
    }

    private static final String IN = "indoor";
    private static final String OUT = "outdoor";
    private static final String LOW = "budget-low";
    private static final String MID = "budget-mid";
    private static final String HIGH = "budget-high";

    /** 업종 규칙: 짧은 접두어부터 적용해서 긴(자세한) 규칙이 실내외·예산을 덮어쓴다 */
    private static final List<Rule> DETAIL = new ArrayList<>();
    /** 이름 규칙: 업종 규칙 다음에 적용 */
    private static final List<Rule> NAME = new ArrayList<>();

    static {
        // ── 상가정보 업종 이름 ──
        detail("노래방", IN, null, "play", "lively");
        detail("당구장", IN, null, "play");
        detail("볼링장", IN, null, "play", "lively");
        detail("전자 게임장", IN, null, "play", "lively");
        detail("기타 오락장", IN, null, "play");
        detail("기타 오락관련 서비스업", IN, null, "play");
        detail("수상/해양 레저업", OUT, null, "play", "lively");
        detail("요리 주점", IN, MID, "lively");
        detail("생맥주 전문", IN, LOW, "lively");
        detail("카페", IN, LOW);
        detail("빵/도넛", IN, LOW, "food-tour");
        detail("아이스크림/빙수", IN, LOW);
        detail("꽃집", IN, null, "shopping", "romantic");
        detail("문구/회화용품 소매업", IN, null, "shopping", "cozy");
        detail("서점", IN, null, "shopping", "quiet", "cozy");
        detail("액세서리/잡화 소매업", IN, null, "shopping", "cozy");
        detail("예술품 소매업", IN, null, "shopping", "unique");
        detail("기념품점", IN, null, "shopping");
        detail("장난감 소매업", IN, null, "shopping", "unique");
        detail("음반/비디오물 소매업", IN, null, "shopping", "cozy", "unique");
        // 음식점: 모두 맛집 투어, 값으로 예산
        for (String cheap : List.of("김밥/만두/분식", "국수/칼국수", "냉면/밀면", "토스트/샌드위치/샐러드", "버거",
                "그 외 기타 간이 음식점", "전/부침개", "베트남식 전문", "기타 동남아식 전문", "치킨", "피자")) {
            detail(cheap, IN, LOW, "food-tour");
        }
        for (String pricey : List.of("소고기 구이/찜", "일식 회/초밥", "횟집", "복 요리 전문", "해산물 구이/찜", "뷔페")) {
            detail(pricey, IN, HIGH, "food-tour");
        }
        detail("파스타/스테이크", IN, HIGH, "food-tour", "romantic");
        detail("패밀리레스토랑", IN, HIGH, "food-tour");
        detail("경양식", IN, MID, "food-tour", "romantic");

        // ── TourAPI 분류 코드 (lclsSystm3) ──
        detail("NA", OUT, null, "walk");                       // 자연관광
        detail("NA0101", OUT, null, "walk", "quiet");          // 산·숲·폭포·계곡
        detail("NA0201", OUT, null, "walk", "romantic");       // 강
        detail("NA0202", OUT, null, "walk", "romantic");       // 호수
        detail("NA020800", OUT, null, "walk", "romantic");     // 해안절경
        detail("NA020900", OUT, null, "walk", "romantic");     // 해변
        detail("NA040700", OUT, null, "walk", "quiet", "cozy"); // 수목원·정원
        detail("NA030100", IN, null, "unique");                // 동굴
        detail("HS01", OUT, null, "walk", "quiet");            // 역사유적지
        detail("HS010100", OUT, null, "walk", "quiet", "night-view"); // 고궁 (야간 개장)
        detail("HS010600", OUT, null, "walk", "cozy");         // 민속마을
        detail("HS02", OUT, null, "walk", "quiet");            // 역사유물
        detail("HS03", OUT, null, "walk", "quiet");            // 종교성지
        detail("HS04", OUT, null, "unique");                   // 안보관광
        detail("VE010100", IN, null, "unique");                // 건물
        detail("VE010200", IN, null, "night-view", "romantic"); // 타워·전망대
        detail("VE010300", OUT, null, "night-view", "walk", "romantic"); // 다리
        detail("VE010400", OUT, null, "night-view", "romantic"); // 분수
        detail("VE010800", OUT, null, "walk", "romantic");     // 등대
        detail("VE010900", OUT, null, "unique");               // 기타 조형물
        detail("VE020100", OUT, null, "play", "lively");       // 테마파크
        detail("VE020200", OUT, null, "play", "lively");       // 워터파크
        detail("VE020300", OUT, null, "walk", "lively");       // 동물원
        detail("VE020400", IN, null, "unique", "romantic");    // 수족관
        detail("VE020500", OUT, null, "night-view", "unique", "romantic"); // 천문대
        detail("VE030100", OUT, null, "walk");                 // 시민공원
        detail("VE030200", OUT, null, "walk");                 // 소공원
        detail("VE030400", OUT, null, "walk");                 // 근린공원
        detail("VE030500", OUT, null, "walk");                 // 주제공원
        detail("VE040100", OUT, null, "walk", "cozy", "shopping"); // 골목길·문화거리
        detail("VE040200", OUT, null, "walk", "cozy");         // 마을관광지
        detail("VE040300", OUT, null, "walk", "quiet");        // 둘레길
        detail("VE050100", OUT, null, "play", "lively");       // 관광단지
        detail("VE060100", IN, null, "exhibition");            // 공연장
        detail("VE060200", IN, null, "exhibition");            // 영화관
        for (String museum : List.of("VE070100", "VE070200", "VE070300", "VE070500")) {
            detail(museum, IN, null, "exhibition", "quiet");    // 박물관·기념관·전시관·과학관
        }
        detail("VE070600", IN, null, "exhibition", "quiet", "cozy"); // 미술관·화랑
        detail("VE090300", IN, null, "quiet");                 // 도서관
        detail("VE090400", IN, null, "exhibition", "workshop"); // 문화전수시설
        detail("VE100100", OUT, null, "lively");               // 스포츠경기장
        detail("VE120100", IN, null, "quiet", "cozy");         // 서점
        detail("VE120300", IN, null, "exhibition");            // 기타문화시설
        detail("EX01", IN, null, "workshop");                  // 전통체험
        detail("EX02", IN, null, "workshop", "cozy");          // 공예체험
        detail("EX03", OUT, null, "workshop");                 // 농·산·어촌 체험
        detail("EX04", OUT, null, "quiet", "workshop");        // 템플스테이·사찰체험
        detail("EX05", IN, null, "quiet");                     // 온천·스파 등
        detail("EX06", IN, null, "workshop");                  // 산업관광
        detail("EX070100", OUT, null, "unique", "night-view", "romantic"); // 유람선
        detail("EX070200", IN, null, "workshop");              // 기타체험
        detail("LS", OUT, null, "play", "lively");             // 레저스포츠
        detail("LS010900", IN, null, "play", "lively");        // 스케이트
        detail("AC05", OUT, null, "unique");                   // 캠핑
        detail("AC050400", OUT, null, "unique", "romantic");   // 글램핑
        detail("FD0", IN, MID, "food-tour");                   // 음식 (TourAPI)
        detail("FD030", IN, LOW, "food-tour");                 // 간이음식
        detail("FD040", IN, MID, "lively");                    // 주점
        detail("FD050", IN, LOW);                              // 카페·찻집
        detail("FD050200", IN, LOW, "quiet", "cozy");          // 찻집

        DETAIL.sort(Comparator.comparingInt((Rule r) -> r.prefix().length()));

        // ── 이름에 든 말 (정리한 이름 기준: 소문자, 공백·기호 없음) ──
        name("루프탑", OUT, "night-view", "romantic");
        name("rooftop", OUT, "night-view", "romantic");
        name("와인", null, "romantic");
        name("wine", null, "romantic");
        name("공방", IN, "workshop", "cozy");
        name("방탈출", IN, "play", "unique");
        name("보드게임", IN, "play");
        name("북카페", IN, "quiet", "cozy");
        name("책방", IN, "quiet", "cozy");
        name("재즈", null, "cozy");
        name("jazz", null, "cozy");
        name("한옥", null, "cozy", "unique");
        name("고양이카페", IN, "unique", "play");
        name("애견카페", IN, "unique", "play");
        name("클라이밍", IN, "play");
        name("인생네컷", IN, "unique", "play");
        name("포토이즘", IN, "unique", "play");
        name("갤러리", IN, "exhibition", "cozy");
        name("gallery", IN, "exhibition", "cozy");
    }

    private PlaceKeywordRules() {
    }

    private static void detail(String prefix, String setting, String budget, String... tags) {
        DETAIL.add(new Rule(prefix, Set.of(tags), setting, budget));
    }

    private static void name(String word, String setting, String... tags) {
        NAME.add(new Rule(word, Set.of(tags), setting, null));
    }

    /** 규칙에 나오는 code 전부 (테스트에서 keyword 시드와 맞는지 본다) */
    static Set<String> allCodes() {
        Set<String> all = new LinkedHashSet<>(List.of(IN, OUT, LOW, MID, HIGH, "food-tour"));
        DETAIL.forEach(r -> all.addAll(r.tags()));
        NAME.forEach(r -> all.addAll(r.tags()));
        return all;
    }

    /** 붙일 키워드 code 들 (keyword.code) */
    public static Set<String> codesFor(PlaceCategory category, String detailCategory, String normalizedName) {
        Set<String> codes = new LinkedHashSet<>();
        String setting = null;
        String budget = null;
        if (detailCategory != null) {
            for (Rule rule : DETAIL) {
                if (detailCategory.startsWith(rule.prefix())) {
                    codes.addAll(rule.tags());
                    setting = rule.setting() == null ? setting : rule.setting();
                    budget = rule.budget() == null ? budget : rule.budget();
                }
            }
        }
        if (normalizedName != null) {
            for (Rule rule : NAME) {
                if (normalizedName.contains(rule.prefix())) {
                    codes.addAll(rule.tags());
                    setting = rule.setting() == null ? setting : rule.setting();
                }
            }
        }
        codes.add(setting != null ? setting : category == PlaceCategory.ATTRACTION ? OUT : IN);
        boolean food = category == PlaceCategory.RESTAURANT || category == PlaceCategory.CAFE || category == PlaceCategory.BAR;
        if (food) {
            codes.add(budget != null ? budget : category == PlaceCategory.CAFE ? LOW : MID);
            if (category == PlaceCategory.RESTAURANT) {
                codes.add("food-tour");
            }
        }
        return codes;
    }
}
