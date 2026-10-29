package com.datecourse.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.datecourse.domain.course.dto.CourseSearchRequest;
import com.datecourse.domain.course.dto.CourseSearchResponse;
import com.datecourse.domain.course.exception.CourseException;
import com.datecourse.domain.course.repository.CourseQueryRepository;
import com.datecourse.domain.keyword.dto.KeywordResponse;
import com.datecourse.domain.keyword.service.KeywordService;
import com.datecourse.global.cache.JsonCache;

class CourseQueryServiceTests {

    CourseQueryRepository repository = mock(CourseQueryRepository.class);
    KeywordService keywordService = mock(KeywordService.class);
    JsonCache cache = mock(JsonCache.class);
    CourseQueryService service = new CourseQueryService(repository, keywordService, cache);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        given(keywordService.all()).willReturn(List.of(
                new KeywordResponse("quiet", "조용", "mood"),
                new KeywordResponse("romantic", "로맨틱", "mood"),
                new KeywordResponse("walk", "산책", "activity"),
                new KeywordResponse("indoor", "실내", "setting")));
        given(cache.getOrLoad(any(), any(java.time.Duration.class), any(), any())).willAnswer(inv -> ((Supplier<Object>) inv.getArgument(3)).get());
    }

    @Test
    void 키워드는_쉼표로도_받고_정렬해서_캐시_키를_맞춘다() {
        CourseSearchRequest a = service.normalize(37.49812, 127.02761, null, List.of("Romantic,quiet"), null);
        CourseSearchRequest b = service.normalize(37.4981, 127.0276, 3000, List.of("quiet", "romantic", "quiet"), 0);
        assertThat(a.keywords()).containsExactly("quiet", "romantic");
        assertThat(a.cacheKey()).isEqualTo(b.cacheKey()).isEqualTo("courses:search:37.498:127.028:3000:quiet,romantic:0");
    }

    @Test
    void 잘못된_입력은_400() {
        assertThatThrownBy(() -> service.normalize(null, 127.0, null, null, null)).isInstanceOf(CourseException.class);
        assertThatThrownBy(() -> service.normalize(35.0, 140.0, null, null, null)).isInstanceOf(CourseException.class);
        assertThatThrownBy(() -> service.normalize(37.5, 127.0, 100, null, null)).isInstanceOf(CourseException.class);
        assertThatThrownBy(() -> service.normalize(37.5, 127.0, null, List.of("nope"), null)).isInstanceOf(CourseException.class);
        assertThatThrownBy(() -> service.normalize(37.5, 127.0, null, List.of("quiet,romantic,walk,indoor"), null))
                .isInstanceOf(CourseException.class);
        assertThatThrownBy(() -> service.normalize(37.5, 127.0, null, null, 10)).isInstanceOf(CourseException.class);
    }

    @Test
    void 검색_결과에_단계와_고른_키워드_중_맞은_것을_붙인다() {
        given(repository.search(eq(37.5), eq(127.0), eq(3000), eq(List.of("quiet", "walk")), anyInt(), anyInt())).willReturn(List.of(
                new CourseQueryRepository.Row(7, "미술관 데이트 코스", "미술관 → 카페", "auto", "서울특별시", "종로구",
                        37.5, 127.0, 200, 123.4, 1)));
        given(repository.steps(anyList())).willReturn(Map.of(7L, List.of(new CourseQueryRepository.StepPlace(7, 1, "spot", 60, null, null,
                11, "미술관", null, "culture", "VE070600", 37.5, 127.0, "서울특별시", "종로구", null, null, null, null, null, true, false, false, false))));
        given(repository.keywords(anyList())).willReturn(Map.of(7L, List.of("quiet", "romantic", "indoor")));

        CourseSearchResponse res = service.search(service.normalize(37.5, 127.0, null, List.of("walk", "quiet"), null));

        assertThat(res.total()).isEqualTo(1);
        assertThat(res.courses().get(0).distanceMeters()).isEqualTo(123);
        assertThat(res.courses().get(0).matchedKeywords()).containsExactly("quiet");
        assertThat(res.courses().get(0).steps()).extracting(s -> s.name()).containsExactly("미술관");
    }

    @Test
    void 근처에_코스가_없으면_두_배씩_넓혀_찾는다() {
        given(repository.search(eq(37.5), eq(127.0), eq(2000), any(), anyInt(), anyInt())).willReturn(List.of());
        given(repository.search(eq(37.5), eq(127.0), eq(4000), any(), anyInt(), anyInt())).willReturn(List.of());
        given(repository.search(eq(37.5), eq(127.0), eq(8000), any(), anyInt(), anyInt())).willReturn(List.of(
                new CourseQueryRepository.Row(7, "코스", "a → b", "auto", "서울특별시", "종로구", 37.5, 127.0, 200, 6000, 1)));
        given(repository.steps(anyList())).willReturn(Map.of());
        given(repository.keywords(anyList())).willReturn(Map.of());

        CourseSearchResponse res = service.search(service.normalize(37.5, 127.0, 2000, null, null));

        assertThat(res.radius()).isEqualTo(8000);
        assertThat(res.widened()).isTrue();
        assertThat(res.total()).isEqualTo(1);
    }

    @Test
    void 십_km_까지_넓혀도_없으면_빈_목록() {
        given(repository.search(anyDouble(), anyDouble(), anyInt(), any(), anyInt(), anyInt())).willReturn(List.of());
        given(repository.steps(anyList())).willReturn(Map.of());
        given(repository.keywords(anyList())).willReturn(Map.of());

        CourseSearchResponse res = service.search(service.normalize(37.5, 127.0, 3000, null, null));

        assertThat(res.radius()).isEqualTo(10_000);
        assertThat(res.courses()).isEmpty();
    }

    @Test
    void 없는_코스는_404() {
        given(repository.detail(5)).willReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> service.detail(5)).isInstanceOf(CourseException.class).hasMessageContaining("찾을 수 없어요");
    }

    @Test
    void 네이버지도_검색_주소() {
        assertThat(CourseQueryService.naverMapUrl("롤링파스타", "가로수길점", "강남구"))
                .isEqualTo("https://map.naver.com/p/search/%EB%A1%A4%EB%A7%81%ED%8C%8C%EC%8A%A4%ED%83%80%20%EA%B0%80%EB%A1%9C%EC%88%98%EA%B8%B8%EC%A0%90%20%EA%B0%95%EB%82%A8%EA%B5%AC");
    }

    @Test
    void 고른_이유는_아는_사실만() {
        CourseQueryRepository.StepPlace local = new CourseQueryRepository.StepPlace(7, 3, "meal", 70, 4, null, 12, "동네식당", null,
                "restaurant", "경양식", 37.5, 127.0, "서울특별시", "종로구", null, null, null, null, null, false, true, true, false);
        CourseQueryRepository.StepPlace museum = new CourseQueryRepository.StepPlace(7, 1, "spot", 60, null, null, 11, "미술관", null,
                "culture", "VE070600", 37.5, 127.0, "서울특별시", "종로구", null, null, null, null, null, true, true, false, false);
        CourseQueryRepository.StepPlace chain = new CourseQueryRepository.StepPlace(7, 2, "cafe", 60, 4, null, 13, "체인카페", null,
                "cafe", "카페", 37.5, 127.0, "서울특별시", "종로구", null, null, null, null, null, false, true, false, true);

        assertThat(CourseQueryService.reasons(local)).containsExactly("모범음식점", "체인 아닌 동네 가게");
        assertThat(CourseQueryService.reasons(museum)).containsExactly("관광공사·상가정보 모두 확인");
        assertThat(CourseQueryService.reasons(chain)).isEmpty();
    }

    static CourseQueryRepository.Candidate cand(long id, String name, String category, String detail, double north,
                                                boolean badge, boolean franchise) {
        return new CourseQueryRepository.Candidate(id, name, null, name, category, detail, 37.5 + north / 111_320, 127.0,
                "서울특별시", "종로구", null, null, null, null, null, java.util.Set.of(), false, true, badge, franchise);
    }

    @Test
    void 근처_대안은_같은_역할에서_좋은_순으로_코스에_든_곳은_빼고() {
        CourseQueryRepository.StepPlace meal = new CourseQueryRepository.StepPlace(7, 2, "meal", 70, 4, null, 12, "원래식당", null,
                "restaurant", "백반/한정식", 37.5, 127.0, "서울특별시", "종로구", null, null, null, null, null, false, true, false, false);
        given(repository.detail(7)).willReturn(java.util.Optional.of(new CourseQueryRepository.Detail(7, "코스", "a → b", "auto",
                "서울특별시", "종로구", 37.5, 127.0, 200, null, null)));
        given(repository.steps(List.of(7L))).willReturn(Map.of(7L, List.of(meal)));
        given(repository.keywords(List.of(7L))).willReturn(Map.of(7L, List.of("romantic")));
        given(repository.candidates(eq(37.5), eq(127.0), anyDouble(), eq(List.of("restaurant")))).willReturn(List.of(
                cand(12, "원래식당", "restaurant", "백반/한정식", 0, false, false),      // 코스에 든 곳
                cand(13, "체인식당", "restaurant", "백반/한정식", 50, false, true),
                cand(14, "모범식당", "restaurant", "백반/한정식", 300, true, false),
                cand(15, "파스타집", "restaurant", "파스타/스테이크", 200, false, false),
                cand(16, "먼식당", "restaurant", "백반/한정식", 900, false, false)));  // 600m 밖

        List<com.datecourse.domain.course.dto.AlternativeResponse> alts = service.alternatives(7, 2);

        assertThat(alts).extracting(a -> a.place().name()).containsExactly("파스타집", "모범식당", "체인식당");
        assertThat(alts.get(1).reasons()).contains("모범음식점");
        assertThat(alts.get(0).distanceMeters()).isEqualTo(200);
    }

    @Test
    void 없는_단계의_대안은_404() {
        given(repository.detail(7)).willReturn(java.util.Optional.of(new CourseQueryRepository.Detail(7, "코스", "a", "auto",
                "서울특별시", "종로구", 37.5, 127.0, 200, null, null)));
        given(repository.steps(List.of(7L))).willReturn(Map.of());
        given(repository.keywords(List.of(7L))).willReturn(Map.of());
        assertThatThrownBy(() -> service.alternatives(7, 9)).isInstanceOf(CourseException.class);
    }

    @Test
    void 코스에_넣을_장소는_이름이_같은_곳_먼저_그다음_가까운_순() {
        given(repository.candidates(eq(37.5), eq(127.0), anyDouble(), anyList(), eq("카페"))).willReturn(List.of(
                cand(21, "동네카페", "cafe", "카페", 100, false, false),
                cand(22, "카페", "cafe", "카페", 800, false, false),
                cand(23, "카페모모", "cafe", "카페", 500, true, false),
                cand(24, "먼카페", "cafe", "카페", 2500, false, false)));  // 2km 밖

        List<com.datecourse.domain.course.dto.AlternativeResponse> found = service.nearbyPlaces(37.5, 127.0, " 카페 ");

        assertThat(found).extracting(a -> a.place().name()).containsExactly("카페", "카페모모", "동네카페");
        assertThat(found.get(1).reasons()).contains("모범음식점");
    }

    @Test
    void 장소_찾기는_검색어가_있어야_한다() {
        assertThatThrownBy(() -> service.nearbyPlaces(37.5, 127.0, "  ")).isInstanceOf(CourseException.class);
        assertThatThrownBy(() -> service.nearbyPlaces(null, 127.0, "카페")).isInstanceOf(CourseException.class);
    }

    @Test
    void 업종으로_찾으면_이름에_글자만_든_가게보다_그_업종이_먼저() {
        given(repository.candidates(eq(37.5), eq(127.0), anyDouble(), anyList(), eq("서점"))).willReturn(List.of(
                cand(31, "한솥도시락역삼세무서점", "restaurant", "김밥/만두/분식", 100, false, false),
                cand(32, "동네책방", "shop", "서점", 600, false, false)));

        assertThat(service.nearbyPlaces(37.5, 127.0, "서점")).extracting(a -> a.place().name()).containsExactly("동네책방", "한솥도시락역삼세무서점");
    }
}
