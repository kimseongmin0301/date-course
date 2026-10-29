package com.datecourse.domain.collector.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AddressKeyTests {

    @Test
    void 시도_층_괄호를_뗀다() {
        assertThat(AddressKey.of("서울특별시 종로구 삼청로 54, 지하1,지상1,2층 (소격동)")).isEqualTo("종로구 삼청로 54");
        assertThat(AddressKey.of("서울특별시 종로구 삼청로 54 (소격동)")).isEqualTo("종로구 삼청로 54");
        assertThat(AddressKey.of("서울특별시 종로구 소격동 48 ")).isEqualTo("종로구 소격동 48");
    }

    @Test
    void 시도_이름이_달라도_같은_열쇠() {
        assertThat(AddressKey.of("강원도 춘천시 중앙로 1")).isEqualTo(AddressKey.of("강원특별자치도 춘천시 중앙로 1"));
        assertThat(AddressKey.of("경기도 수원시 장안구 정조로 1")).isEqualTo("수원시 장안구 정조로 1");
    }

    @Test
    void 너무_짧으면_맞춰_보지_않는다() {
        assertThat(AddressKey.of(null)).isNull();
        assertThat(AddressKey.of("  ")).isNull();
        assertThat(AddressKey.of("서울특별시 종로구")).isNull();
    }
}
