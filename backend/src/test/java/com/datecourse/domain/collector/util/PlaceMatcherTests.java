package com.datecourse.domain.collector.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.datecourse.domain.place.entity.PlaceCategory;

class PlaceMatcherTests {

    static final double LAT = 37.5;
    static final double LNG = 127.0;
    static final PlaceCategory CAFE = PlaceCategory.CAFE;

    static PlaceMatcher.Known at(long id, String branch, double metersNorth) {
        return at(id, branch, CAFE, metersNorth);
    }

    static PlaceMatcher.Known at(long id, String branch, PlaceCategory category, double metersNorth) {
        return new PlaceMatcher.Known(id, branch, category, LAT + Geo.latDelta(metersNorth), LNG);
    }

    @Test
    void 가장_가까운_50m_안을_같은_장소로() {
        PlaceMatcher.Match m = PlaceMatcher.match(List.of(at(1, null, 40), at(2, null, 10), at(3, null, 120)), null, CAFE, LAT, LNG);
        assertThat(m.sameId()).isEqualTo(2L);
        assertThat(m.nearId()).isNull();
    }

    @Test
    void 거리_50에서_150m_는_중복_후보() {
        PlaceMatcher.Match m = PlaceMatcher.match(List.of(at(1, null, 120), at(2, null, 90)), null, CAFE, LAT, LNG);
        assertThat(m.sameId()).isNull();
        assertThat(m.nearId()).isEqualTo(2L);
        assertThat(m.nearDistance()).isBetween(89.0, 91.0);
    }

    @Test
    void 지점명이_다르면_건너뛰고_한쪽만_있으면_비교한다() {
        assertThat(PlaceMatcher.match(List.of(at(1, "강남점", 5)), "역삼점", CAFE, LAT, LNG).sameId()).isNull();
        assertThat(PlaceMatcher.match(List.of(at(1, "강남점", 5)), null, CAFE, LAT, LNG).sameId()).isEqualTo(1L);
    }

    @Test
    void 관광명소는_관광명소끼리만() {
        // 관광지 "인사동" 바로 옆의 가게 "인사동"
        assertThat(PlaceMatcher.match(List.of(at(1, null, PlaceCategory.ATTRACTION, 5)), null, PlaceCategory.SHOP, LAT, LNG).sameId()).isNull();
        assertThat(PlaceMatcher.match(List.of(at(1, null, PlaceCategory.ATTRACTION, 5)), null, PlaceCategory.ATTRACTION, LAT, LNG).sameId()).isEqualTo(1L);
        // 문화시설 ↔ 서점, 음식점 ↔ 카페 는 출처마다 분류가 달라도 같은 곳일 수 있다
        assertThat(PlaceMatcher.match(List.of(at(1, null, PlaceCategory.CULTURE, 5)), null, PlaceCategory.SHOP, LAT, LNG).sameId()).isEqualTo(1L);
        assertThat(PlaceMatcher.match(List.of(at(1, null, PlaceCategory.RESTAURANT, 5)), null, CAFE, LAT, LNG).sameId()).isEqualTo(1L);
    }

    @Test
    void 거리_150m_밖은_없음() {
        PlaceMatcher.Match m = PlaceMatcher.match(List.of(at(1, null, 300)), null, CAFE, LAT, LNG);
        assertThat(m.sameId()).isNull();
        assertThat(m.nearId()).isNull();
    }
}
