package com.datecourse.domain.course.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.datecourse.domain.course.entity.StepRole;
import com.datecourse.domain.place.entity.PlaceCategory;

class CoursePlannerTests {

    /** 기준점에서 북쪽으로 meters 만큼 (위도 1도 ≈ 111,320m) */
    static final double LAT = 37.5;
    static final double LNG = 127.0;

    static CoursePlanner.Spot spot(long id, String name, PlaceCategory category, String detail, double northMeters,
                                   Set<String> keywords, boolean tour, boolean franchise) {
        return new CoursePlanner.Spot(id, name, name, category, detail, LAT + northMeters / 111_320, LNG,
                "서울특별시", "강남구", keywords, tour, franchise);
    }

    static final CoursePlanner.Spot ANCHOR = spot(1, "미술관", PlaceCategory.CULTURE, "VE070600", 0,
            Set.of("exhibition", "quiet", "cozy", "indoor"), true, false);

    @Test
    void 명소_소품샵_카페_식당_순서로_만든다() {
        PlaceGrid grid = new PlaceGrid();
        grid.add(ANCHOR);
        grid.add(spot(2, "소품샵", PlaceCategory.SHOP, "액세서리/잡화 소매업", 200, Set.of("shopping", "cozy", "indoor"), false, false));
        grid.add(spot(3, "조용한카페", PlaceCategory.CAFE, "카페", 400, Set.of("indoor", "budget-low"), false, false));
        grid.add(spot(4, "파스타집", PlaceCategory.RESTAURANT, "파스타/스테이크", 600,
                Set.of("food-tour", "romantic", "indoor", "budget-high"), false, false));

        CoursePlanner.Plan plan = CoursePlanner.plan(ANCHOR, grid).orElseThrow();

        assertThat(plan.steps()).extracting(s -> s.place().id()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(plan.steps()).extracting(CoursePlanner.Step::role)
                .containsExactly(StepRole.SPOT, StepRole.SPOT, StepRole.CAFE, StepRole.MEAL);
        assertThat(plan.summary()).isEqualTo("미술관 → 소품샵 → 조용한카페 → 파스타집");
        assertThat(plan.steps().get(0).walkMinutes()).isNull();
        assertThat(plan.steps().get(1).walkMinutes()).isEqualTo(4); // 200m × 1.3 ÷ 67
        // 구경거리 키워드 + 식당 분위기 + 중심 장소 실내외 + 식당 예산 (식당 맛집 투어는 TourAPI 식당만)
        assertThat(plan.keywords()).containsExactlyInAnyOrder("exhibition", "quiet", "cozy", "shopping", "romantic", "indoor", "budget-high");
        assertThat(plan.totalMinutes()).isEqualTo(60 + 30 + 4 + 60 + 4 + 70 + 4);
    }

    @Test
    void 카페나_식당이_없으면_만들지_않는다() {
        PlaceGrid grid = new PlaceGrid();
        grid.add(ANCHOR);
        grid.add(spot(3, "카페", PlaceCategory.CAFE, "카페", 300, Set.of("indoor"), false, false));
        assertThat(CoursePlanner.plan(ANCHOR, grid)).isEmpty();
    }

    @Test
    void 구경거리가_없으면_3단계() {
        PlaceGrid grid = new PlaceGrid();
        grid.add(ANCHOR);
        grid.add(spot(3, "카페", PlaceCategory.CAFE, "카페", 300, Set.of("indoor"), false, false));
        grid.add(spot(4, "식당", PlaceCategory.RESTAURANT, "백반/한정식", 500, Set.of("indoor"), false, false));
        assertThat(CoursePlanner.plan(ANCHOR, grid).orElseThrow().steps()).hasSize(3);
    }

    @Test
    void 너무_먼_곳은_넣지_않는다() {
        PlaceGrid grid = new PlaceGrid();
        grid.add(ANCHOR);
        grid.add(spot(3, "카페", PlaceCategory.CAFE, "카페", 800, Set.of("indoor"), false, false)); // 한 구간 700m 넘음
        grid.add(spot(4, "식당", PlaceCategory.RESTAURANT, "백반/한정식", 300, Set.of("indoor"), false, false));
        assertThat(CoursePlanner.plan(ANCHOR, grid)).isEmpty();
    }

    @Test
    void 체인점보다_분위기가_맞는_곳을_고른다() {
        PlaceGrid grid = new PlaceGrid();
        grid.add(ANCHOR);
        grid.add(spot(3, "체인카페", PlaceCategory.CAFE, "카페", 100, Set.of("indoor"), false, true));
        grid.add(spot(5, "북카페", PlaceCategory.CAFE, "카페", 300, Set.of("indoor", "quiet", "cozy"), false, false));
        grid.add(spot(4, "식당", PlaceCategory.RESTAURANT, "백반/한정식", 400, Set.of("indoor"), false, false));
        Optional<CoursePlanner.Plan> plan = CoursePlanner.plan(ANCHOR, grid);
        assertThat(plan.orElseThrow().steps().get(1).place().name()).isEqualTo("북카페");
    }

    @Test
    void 교통시설은_중심_장소가_아니다() {
        assertThat(CoursePlanner.isAnchor(spot(9, "역", PlaceCategory.ATTRACTION, "VE110200", 0, Set.of(), true, false))).isFalse();
        assertThat(CoursePlanner.isAnchor(ANCHOR)).isTrue();
        assertThat(CoursePlanner.isAnchor(spot(9, "카페", PlaceCategory.CAFE, "카페", 0, Set.of(), false, false))).isFalse();
    }

    @Test
    void 지난_행사는_중심_장소가_아니다() {
        assertThat(CoursePlanner.isAnchor(spot(9, "2025 서울 페어", PlaceCategory.ATTRACTION, "EX070200", 0, Set.of(), true, false))).isFalse();
    }

    @Test
    void 서점은_이름이_서점인_곳만_구경거리() {
        assertThat(CoursePlanner.isSecondSpot(spot(9, "동네책방", PlaceCategory.SHOP, "서점", 0, Set.of(), false, false))).isTrue();
        assertThat(CoursePlanner.isSecondSpot(spot(9, "에듀코칭", PlaceCategory.SHOP, "서점", 0, Set.of(), false, false))).isFalse();
        assertThat(CoursePlanner.isSecondSpot(spot(9, "문구", PlaceCategory.SHOP, "문구/회화용품 소매업", 0, Set.of(), false, false))).isFalse();
    }

    @Test
    void 여러_코스에_넣은_카페는_조금씩_아래로() {
        PlaceGrid grid = new PlaceGrid();
        grid.add(ANCHOR);
        grid.add(spot(3, "카페A", PlaceCategory.CAFE, "카페", 100, Set.of("indoor"), false, false));
        grid.add(spot(5, "카페B", PlaceCategory.CAFE, "카페", 200, Set.of("indoor"), false, false));
        grid.add(spot(4, "식당", PlaceCategory.RESTAURANT, "백반/한정식", 400, Set.of("indoor"), false, false));
        java.util.Map<Long, Integer> used = new java.util.HashMap<>();
        assertThat(CoursePlanner.plan(ANCHOR, grid, used).orElseThrow().steps().get(1).place().name()).isEqualTo("카페A");
        assertThat(CoursePlanner.plan(ANCHOR, grid, used).orElseThrow().steps().get(1).place().name()).isEqualTo("카페B");
    }

    @Test
    void 꽃집과_노래방은_구경거리로_넣지_않는다() {
        assertThat(CoursePlanner.isSecondSpot(spot(9, "꽃", PlaceCategory.SHOP, "꽃집", 0, Set.of(), false, false))).isFalse();
        assertThat(CoursePlanner.isSecondSpot(spot(9, "노래", PlaceCategory.ACTIVITY, "노래방", 0, Set.of("play"), false, false))).isFalse();
        assertThat(CoursePlanner.isSecondSpot(spot(9, "방탈출", PlaceCategory.ACTIVITY, "기타 오락관련 서비스업", 0,
                Set.of("play", "unique"), false, false))).isTrue();
    }

    @Test
    void 동네_코스는_명소가_아닌_구경거리를_중심으로() {
        CoursePlanner.Spot shop = spot(20, "소품샵", PlaceCategory.SHOP, "액세서리/잡화 소매업", 0, Set.of("shopping", "cozy", "indoor"), false, false);
        CoursePlanner.Spot bowling = spot(21, "볼링장", PlaceCategory.ACTIVITY, "볼링장", 0, Set.of("play", "lively", "indoor"), false, false);
        CoursePlanner.Spot karaoke = spot(22, "노래방", PlaceCategory.ACTIVITY, "노래방", 0, Set.of("play", "lively"), false, false);
        assertThat(CoursePlanner.isNeighborhoodAnchor(shop)).isTrue();
        assertThat(CoursePlanner.isNeighborhoodAnchor(bowling)).isTrue();
        assertThat(CoursePlanner.isNeighborhoodAnchor(karaoke)).isFalse();
        assertThat(CoursePlanner.isNeighborhoodAnchor(ANCHOR)).isFalse(); // 명소는 원래 코스로

        PlaceGrid grid = new PlaceGrid();
        grid.add(shop);
        grid.add(spot(3, "카페", PlaceCategory.CAFE, "카페", 200, Set.of("indoor"), false, false));
        grid.add(spot(4, "식당", PlaceCategory.RESTAURANT, "경양식", 400, Set.of("indoor", "romantic"), false, false));
        assertThat(CoursePlanner.plan(shop, grid).orElseThrow().summary()).isEqualTo("소품샵 → 카페 → 식당");
    }

    @Test
    void 동네_코스_중심은_체인점보다_분위기_있는_곳() {
        CoursePlanner.Spot chain = spot(30, "체인잡화", PlaceCategory.SHOP, "액세서리/잡화 소매업", 0, Set.of("cozy"), false, true);
        CoursePlanner.Spot local = spot(31, "동네책방", PlaceCategory.SHOP, "서점", 0, Set.of("quiet", "cozy"), false, false);
        assertThat(CoursePlanner.anchorRank(local)).isGreaterThan(CoursePlanner.anchorRank(chain));
    }
}
