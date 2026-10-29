package com.datecourse.domain.place.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NaverLocalSearchClientTests {

    @Test
    void 좌표는_도_단위와_1e7_정수를_모두_읽는다() {
        assertThat(NaverLocalSearchClient.coordinate("129.4464045")).isEqualTo(129.4464045);
        assertThat(NaverLocalSearchClient.coordinate("1294464045")).isEqualTo(129.4464045, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(NaverLocalSearchClient.coordinate("")).isZero();
    }

    @Test
    void 이름의_강조_태그와_글자_참조를_지운다() {
        assertThat(NaverLocalSearchClient.plain("<b>카페</b> 높 &amp; 베이커리")).isEqualTo("카페 높 & 베이커리");
    }

    @Test
    void 키가_없으면_부르지_않는다() {
        NaverLocalSearchClient client = new NaverLocalSearchClient("http://localhost:1", "", "");
        assertThat(client.configured()).isFalse();
        assertThat(client.search("카페")).isEmpty();
    }
}
