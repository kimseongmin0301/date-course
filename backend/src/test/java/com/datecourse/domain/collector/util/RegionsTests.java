package com.datecourse.domain.collector.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegionsTests {

    @Test
    void 시도와_시군구() {
        assertThat(Regions.parse("서울특별시 강남구 테헤란로 1")).isEqualTo(new Regions.Region("서울특별시", "강남구"));
        assertThat(Regions.parse("강원특별자치도 춘천시 중앙로 1")).isEqualTo(new Regions.Region("강원특별자치도", "춘천시"));
    }

    @Test
    void 구가_있는_시는_함께() {
        assertThat(Regions.parse("경기도 수원시 장안구 정조로 1")).isEqualTo(new Regions.Region("경기도", "수원시 장안구"));
    }

    @Test
    void 시군구가_없는_시도() {
        assertThat(Regions.parse("세종특별자치시 한누리대로 2130")).isEqualTo(new Regions.Region("세종특별자치시", null));
        assertThat(Regions.parse(" ")).isNull();
    }
}
