package com.datecourse.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.datecourse.domain.ai.repository.AiRepository;
import com.datecourse.domain.ai.util.QueryRules;

import tools.jackson.databind.json.JsonMapper;

/** AI 검색 규칙, AI 답 검수, 코스 임베딩 글·비슷한 코스 찾기 (Ollama·DB 없이) */
class AiTests {

    JsonMapper json = JsonMapper.builder().build();

    @Test
    void 규칙은_날씨_예산_비슷한_말_지역_후보를_알아듣는다() {
        QueryRules.Parsed p = QueryRules.parse("비 오는 토요일 저녁에 성수동에서 조용하게 데이트, 둘이 5만원 정도");

        assertThat(p.setting()).isEqualTo("indoor");          // 비 → 실내 (작은 모델은 야외로 잘못 잡았음)
        assertThat(p.budget()).isEqualTo("budget-mid");       // 5만원 → 3~7만원
        assertThat(p.keywords()).containsExactly("quiet");
        assertThat(p.areaCandidates()).first().isEqualTo("성수동");   // 조사 "에서"를 떼고, 동으로 끝나는 말 먼저
    }

    @Test
    void 풍경과_시도는_지점이_아니라_범위로_알아듣는다() {
        QueryRules.Parsed busan = QueryRules.parse("부산 바닷가");
        assertThat(busan.sidos()).containsExactly("부산광역시");   // 예전엔 "부산" → 같은 글자로 시작하는 부산진구(서면)
        assertThat(busan.scene()).isEqualTo("sea");
        assertThat(busan.areaCandidates()).isEmpty();

        QueryRules.Parsed sea = QueryRules.parse("바다 보러 가고 싶어");
        assertThat(sea.scene()).isEqualTo("sea");
        assertThat(sea.sidos()).isEmpty();                         // 지역이 없으면 전국에서

        assertThat(QueryRules.parse("전라도 바다").sidos()).containsExactly("전북특별자치도", "전남광주통합특별시");
        QueryRules.Parsed west = QueryRules.parse("서해 노을");
        assertThat(west.scene()).isEqualTo("sea");
        assertThat(west.sidos()).contains("충청남도", "인천광역시");

        // 시도 이름처럼 시작하지만 지역(구·시)인 말은 지점 후보로
        assertThat(QueryRules.parse("부산진구 맛집").sidos()).isEmpty();
        assertThat(QueryRules.parse("제주시 카페").sidos()).isEmpty();
        assertThat(QueryRules.parse("제주도 카페").sidos()).containsExactly("제주특별자치도");
        assertThat(QueryRules.parse("산책하기 좋은 곳").scene()).isNull();   // 산책은 산이 아님
        assertThat(QueryRules.parse("제주 바다 감성 카페").areaCandidates()).isEmpty();   // "카페"는 지역 이름이 아님

        // 명소는 이름이 낱말로 시작할 때만, 시도를 알아들었으면 그 시도 안의 것만
        var cafeVillage = new com.datecourse.domain.place.dto.LocationResult("place", "카페도자기마을", "경기도 남양주시", 37.56, 127.31, 1500);
        var gwangalli = new com.datecourse.domain.place.dto.LocationResult("place", "광안리해수욕장", "부산광역시 수영구", 35.15, 129.12, 1500);
        assertThat(AiParseService.pickArea("도자기", List.of(cafeVillage), List.of())).isNull();
        assertThat(AiParseService.pickArea("광안리", List.of(gwangalli), List.of("부산광역시"))).isEqualTo(gwangalli);
        assertThat(AiParseService.pickArea("광안리", List.of(gwangalli), List.of("제주특별자치도"))).isNull();
    }

    @Test
    void 비_오는_날_풍경은_실내로_바꾸지_않는다() {
        QueryRules.Parsed p = QueryRules.parse("비 오는 날 바다 보러 가고 싶어");
        assertThat(p.scene()).isEqualTo("sea");
        assertThat(p.rainy()).isTrue();
        assertThat(p.setting()).isNull();                          // 비 오는 날의 바다를 원함

        assertThat(QueryRules.parse("비 오는 날 성수동 데이트").setting()).isEqualTo("indoor");   // 풍경이 없으면 그대로 실내
        assertThat(QueryRules.parse("추워서 실내에서 바다 보고 싶어").setting()).isEqualTo("indoor");   // 실내를 바라면 실내
    }

    @Test
    void 규칙_예산과_실내외의_다른_말() {
        assertThat(QueryRules.parse("가성비 좋은 야외 데이트").budget()).isEqualTo("budget-low");
        assertThat(QueryRules.parse("가성비 좋은 야외 데이트").setting()).isEqualTo("outdoor");
        assertThat(QueryRules.parse("기념일이라 고급스럽게 10만원").budget()).isEqualTo("budget-high");
        assertThat(QueryRules.parse("기념일이라 고급스럽게 10만원").keywords()).contains("romantic");
        assertThat(QueryRules.parse("홍대 방탈출하고 야경").keywords()).containsExactly("play", "night-view");
    }

    @Test
    void 메뉴는_지역_후보가_아니라_메뉴로_알아듣는다() {
        QueryRules.Parsed p = QueryRules.parse("안양일번가 햄버거");
        assertThat(p.food()).isEqualTo("burger");
        assertThat(p.areaCandidates()).containsExactly("안양일번가");
        assertThat(QueryRules.parse("성수동 수제버거집").food()).isEqualTo("burger");
        assertThat(QueryRules.parse("강남 회 먹으러").food()).isEqualTo("sushi");
        assertThat(QueryRules.parse("회사 근처 데이트").food()).isNull();   // 한 글자 메뉴(회)는 낱말이 같을 때만
        assertThat(QueryRules.parse("홍대 빵집 투어").food()).isEqualTo("bakery");   // "빵집"은 STOP 이 아니라 메뉴
        assertThat(QueryRules.parse("조용한 카페").food()).isNull();
    }

    @Test
    void AI_답은_원래_문장에_있는_지역과_정해진_키워드만_받는다() throws Exception {
        Map<String, com.datecourse.domain.keyword.dto.KeywordResponse> byName = Map.of(
                "조용", new com.datecourse.domain.keyword.dto.KeywordResponse("quiet", "조용", "mood"),
                "전시·문화", new com.datecourse.domain.keyword.dto.KeywordResponse("exhibition", "전시·문화", "activity"),
                "실내", new com.datecourse.domain.keyword.dto.KeywordResponse("indoor", "실내", "setting"),
                "3~7만원", new com.datecourse.domain.keyword.dto.KeywordResponse("budget-mid", "3~7만원", "budget"));
        var ai = json.readTree("""
                {"area":"강남","keywords":["조용","전시·문화","미식가"],"setting":"실내","budget":"3~7만원"}""");

        var accepted = com.datecourse.domain.ai.service.AiTestAccess.accept(ai, "성수동 조용한 전시 데이트, 실내에서 3만원 넘게", byName);

        assertThat(accepted.area()).isNull();                                  // 문장에 없는 "강남"은 버림
        assertThat(accepted.moods()).containsExactly("quiet", "exhibition");   // 목록에 없는 "미식가"는 버림
        assertThat(accepted.setting()).isEqualTo("indoor");
        assertThat(accepted.budget()).isEqualTo("budget-mid");

        // 문장에 실내외·돈 이야기가 없으면 AI 가 정해도 받지 않는다
        var guessed = com.datecourse.domain.ai.service.AiTestAccess.accept(ai, "첫 데이트라 성수동 조용한 곳", byName);
        assertThat(guessed.setting()).isNull();
        assertThat(guessed.budget()).isNull();
    }

    @Test
    void AI_추천은_후보_번호만_받고_이유를_검수한다() throws Exception {
        var a = com.datecourse.domain.ai.service.AiTestAccess.candidate(1, "도을 데이트 코스", List.of("수눌음", "도을", "낭뜰에쉼팡"));
        var b = com.datecourse.domain.ai.service.AiTestAccess.candidate(2, "강동 바다 코스", List.of("강동바다도서관", "에르벵커피하우스", "가고파"));
        var answer = json.readTree("""
                {"picks":[
                  {"n":2,"reason":"바다 앞 도서관과 카페로 조용해요"},
                  {"n":9,"reason":"후보에 없는 번호"},
                  {"n":2,"reason":"같은 번호 두 번"},
                  {"n":1,"reason":"에르벵커피하우스에서 쉬기 좋아요"}]}""");

        var picks = com.datecourse.domain.ai.service.AiTestAccess.accept(answer, List.of(a, b));

        assertThat(picks).extracting(p -> p.courseId()).containsExactly(1L, 2L);   // 순위(후보 번호) 순서로
        assertThat(picks.get(1).reason()).isEqualTo("바다 앞 도서관과 카페로 조용해요");
        assertThat(picks.get(0).reason()).isNull();    // 다른 코스(2번)의 가게 이름을 1번 이유로 씀 → 이유만 뺌

        // 번호만 준 답([2, 1])도 받는다 (이유 없이)
        var bare = com.datecourse.domain.ai.service.AiTestAccess.accept(json.readTree("{\"picks\":[2, 1, 9]}"), List.of(a, b));
        assertThat(bare).extracting(p -> p.courseId()).containsExactly(1L, 2L);
        assertThat(bare).allMatch(p -> p.reason() == null);
        assertThat(com.datecourse.domain.ai.service.AiTestAccess.checkReason("2만원이면 충분해요", a, List.of(a, b))).isNull();   // 지어낸 가격
        // 다른 후보 장소 이름의 일부("송도해상케이블카(스카이)" 안의 "송도해상케이블카")를 조사를 붙여 써도 걸러낸다
        var c = com.datecourse.domain.ai.service.AiTestAccess.candidate(3, "송도 코스", List.of("송도해상케이블카(스카이)", "카페"));
        assertThat(com.datecourse.domain.ai.service.AiTestAccess.checkReason("송도해상케이블카에서 바다를 봐요", a, List.of(a, c))).isNull();
        assertThat(com.datecourse.domain.ai.service.AiTestAccess.checkReason("도을 카페에서 조용히 쉬기 좋아요", a, List.of(a, c))).isEqualTo("도을 카페에서 조용히 쉬기 좋아요");
    }

    @Test
    void 코스_임베딩_글과_비슷한_코스_찾기() {
        var text = com.datecourse.domain.ai.service.AiTestAccess.text(new AiRepository.CourseText(13273, "도을 데이트 코스", "제주특별자치도", "제주시", 166,
                List.of("수눌음|shop|기념품점", "도을|cafe|카페", "용두암|attraction|VE070600"), "감성, 실내"));
        assertThat(text).isEqualTo("제주특별자치도 제주시 도을 데이트 코스. 수눌음(기념품점) → 도을(카페) → 용두암(관광명소). 키워드: 감성, 실내.");

        var service = com.datecourse.domain.ai.service.AiTestAccess.indexOf(List.of(
                new AiRepository.Stored(1, 37.50, 127.00, new float[] {1, 0}),
                new AiRepository.Stored(2, 37.50, 127.00, new float[] {0.6f, 0.8f}),
                new AiRepository.Stored(3, 35.10, 129.00, new float[] {1, 0})));   // 멀리 있는 코스
        assertThat(service.similar(new float[] {1, 0}, 37.5, 127.0, 3000, Set.of(), 5)).containsExactly(1L, 2L);
        assertThat(service.similar(new float[] {1, 0}, 37.5, 127.0, 3000, Set.of(1L), 5)).containsExactly(2L);
    }
}
