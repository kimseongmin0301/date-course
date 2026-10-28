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

    @Test
    void 줄이거나_틀리게_쓴_시도는_공식_이름으로() {
        assertThat(Regions.parse("경남 사천시 신수동 산34-5")).isEqualTo(new Regions.Region("경상남도", "사천시"));
        assertThat(Regions.parse("전남광주 완도군 완도읍 장좌리")).isEqualTo(new Regions.Region("전남광주통합특별시", "완도군"));
        assertThat(Regions.canonicalSido("광주광역사")).isEqualTo("전남광주통합특별시");
        assertThat(Regions.canonicalSido("전라북도")).isEqualTo("전북특별자치도");
        assertThat(Regions.canonicalSido("부산광역")).isEqualTo("부산광역시");
        assertThat(Regions.canonicalSido("제주특별자치")).isEqualTo("제주특별자치도");
    }

    @Test
    void 시도를_알_수_없으면_null() {
        assertThat(Regions.parse("닫기")).isNull();
        assertThat(Regions.parse("김제시 요촌동 1")).isNull();
    }
}
