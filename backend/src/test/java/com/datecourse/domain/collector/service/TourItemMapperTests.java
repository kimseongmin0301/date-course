package com.datecourse.domain.collector.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.datecourse.domain.collector.dto.PlaceCandidate;
import com.datecourse.domain.place.entity.PlaceCategory;
import com.datecourse.domain.place.entity.PlaceSourceType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TourItemMapperTests {

    final JsonMapper json = JsonMapper.builder().build();

    JsonNode item(String contentType, String title, String addr, String lclsSystm2, String cat3) {
        return json.readTree("""
                {"contentid":"123","contenttypeid":"%s","title":"%s","addr1":"%s","mapx":"127.0592","mapy":"37.5119",
                 "tel":"02-111-2222<br>02-333-4444","firstimage":"http://example.com/a.jpg",
                 "lclsSystm2":"%s","lclsSystm3":"%s0100","cat3":"%s"}
                """.formatted(contentType, title, addr, lclsSystm2, lclsSystm2, cat3));
    }

    @Test
    void 관광지_항목을_장소_후보로() {
        PlaceCandidate c = TourItemMapper.toCandidate(item("12", "별마당 도서관", "서울특별시 강남구 영동대로 513", "VE01", "A02")).orElseThrow();

        assertThat(c.source()).isEqualTo(PlaceSourceType.TOUR);
        assertThat(c.externalId()).isEqualTo("123");
        assertThat(c.category()).isEqualTo(PlaceCategory.ATTRACTION);
        assertThat(c.lat()).isEqualTo(37.5119);
        assertThat(c.lng()).isEqualTo(127.0592);
        assertThat(c.sido()).isEqualTo("서울특별시");
        assertThat(c.sigungu()).isEqualTo("강남구");
        assertThat(c.roadAddress()).isEqualTo("서울특별시 강남구 영동대로 513");
        assertThat(c.phone()).isEqualTo("02-111-2222");
        assertThat(c.imageUrl()).isEqualTo("http://example.com/a.jpg");
    }

    @Test
    void 음식점_중_카페는_카페로() {
        assertThat(TourItemMapper.toCandidate(item("39", "카페 온", "서울특별시 종로구 북촌로 1", "FD05", "A05020900")).orElseThrow().category())
                .isEqualTo(PlaceCategory.CAFE);
        assertThat(TourItemMapper.toCandidate(item("39", "빵집 카페", "서울특별시 종로구 북촌로 1", "FD03", "A05020900")).orElseThrow().category())
                .isEqualTo(PlaceCategory.CAFE);
        assertThat(TourItemMapper.toCandidate(item("39", "한식당", "서울특별시 종로구 북촌로 1", "FD01", "A05020100")).orElseThrow().category())
                .isEqualTo(PlaceCategory.RESTAURANT);
    }

    @Test
    void 지점명은_따로() {
        PlaceCandidate c = TourItemMapper.toCandidate(item("39", "스타벅스 강남점", "서울특별시 강남구 강남대로 1", "FD05", "A05020900")).orElseThrow();
        assertThat(c.name()).isEqualTo("스타벅스");
        assertThat(c.branch()).isEqualTo("강남점");
        assertThat(c.rawName()).isEqualTo("스타벅스 강남점");
    }

    @Test
    void 쓸_수_없는_항목은_건너뛴다() {
        assertThat(TourItemMapper.toCandidate(item("15", "축제", "서울특별시 중구 세종대로 1", "EV01", "A02"))).isEmpty();
        assertThat(TourItemMapper.toCandidate(item("12", "주소 없음", "", "VE01", "A02"))).isEmpty();
        assertThat(TourItemMapper.toCandidate(json.readTree("""
                {"contentid":"1","contenttypeid":"12","title":"좌표 없음","addr1":"서울특별시 중구 세종대로 1","mapx":"0","mapy":"0"}
                """))).isEmpty();
    }
}
