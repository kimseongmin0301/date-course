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
        NaverLocalSearchClient client = new NaverLocalSearchClient("http://localhost:1", "", "", tools.jackson.databind.json.JsonMapper.builder().build());
        assertThat(client.configured()).isFalse();
        assertThat(client.search("카페")).isEmpty();
    }

    @Test
    void 응답_JSON_을_읽는다() {
        NaverLocalSearchClient client = new NaverLocalSearchClient("http://localhost:1", "", "", tools.jackson.databind.json.JsonMapper.builder().build());
        String body = """
                {"total":2,"items":[
                  {"title":"<b>카페</b> 높","category":"카페,디저트>카페","address":"울산광역시 북구 정자동 434-14",
                   "roadAddress":"울산광역시 북구 정자1길 111","mapx":"1294464045","mapy":"356215371"},
                  {"title":"좌표 없음","mapx":"","mapy":""}]}""";
        assertThat(client.parse(body)).containsExactly(new NaverLocalSearchClient.Item("카페 높", "카페,디저트>카페",
                "울산광역시 북구 정자동 434-14", "울산광역시 북구 정자1길 111", 35.6215371, 129.4464045));
    }

    @Test
    void 부르다_실패하면_503() {
        NaverLocalSearchClient client = new NaverLocalSearchClient("http://localhost:1", "id", "secret", tools.jackson.databind.json.JsonMapper.builder().build());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.search("카페"))
                .isInstanceOf(com.datecourse.domain.place.exception.PlaceException.class);
    }
}
