package com.datecourse.domain.collector.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelRestaurantImporterTests {

    static final Map<String, List<ModelRestaurantImporter.Known>> PLACES = Map.of(
            "종로구 삼청로 54", List.of(new ModelRestaurantImporter.Known(1, "국제갤러리"), new ModelRestaurantImporter.Known(2, "더레스토랑")),
            "종로구 계동 140-43", List.of(new ModelRestaurantImporter.Known(3, "뉴믹스커피")),
            "북구 판지1길 62", List.of(new ModelRestaurantImporter.Known(4, "곽암")));

    static ModelRestaurantImporter.Designation d(String name, String road, String lot) {
        return new ModelRestaurantImporter.Designation("x", name, road, lot, null);
    }

    @Test
    void 같은_주소에서_이름이_같은_곳() {
        assertThat(ModelRestaurantImporter.match(d("더레스토랑", "서울특별시 종로구 삼청로 54, 지하1층 (소격동)", ""), PLACES)).isEqualTo(2L);
    }

    @Test
    void 지점명이_붙거나_이름을_품으면_같은_가게() {
        // 도로명이 없으면 지번으로
        assertThat(ModelRestaurantImporter.match(d("뉴믹스커피 안국점", "", "서울특별시 종로구 계동 140-43 "), PLACES)).isEqualTo(3L);
        assertThat(ModelRestaurantImporter.match(d("곽암 횟집", "울산광역시 북구 판지1길 62", ""), PLACES)).isEqualTo(4L);
    }

    @Test
    void 주소가_같아도_이름이_다르면_아니다() {
        assertThat(ModelRestaurantImporter.match(d("삼청동수제비", "서울특별시 종로구 삼청로 54", ""), PLACES)).isNull();
        assertThat(ModelRestaurantImporter.match(d("더레스토랑", "부산광역시 중구 중앙대로 1", ""), PLACES)).isNull();
    }

    @Test
    void 영업_중이고_지정이_살아_있는_것만_읽는다(@TempDir Path dir) throws Exception {
        String csv = String.join("\n",
                "관리번호,업소명,도로명주소,소재지주소,영업상태명,지정일자,지정취소일자,재지정일자",
                "A1,더레스토랑,\"서울특별시 종로구 삼청로 54, 지하1층 (소격동)\",서울특별시 종로구 소격동 48,영업,2007-11-20,,2025-12-10",
                "A2,문닫은집,서울특별시 종로구 삼청로 1,,폐업,2007-11-20,,",
                "A3,취소된집,서울특별시 종로구 삼청로 2,,영업,2007-11-20,2021-12-16,",
                "A4,\"줄바꿈",
                "식당\",서울특별시 종로구 삼청로 3,,영업,2010-01-01,,");
        Path file = dir.resolve("모범음식점.csv");
        Files.write(file, csv.getBytes(Charset.forName("MS949")));

        List<ModelRestaurantImporter.Designation> list = ModelRestaurantImporter.read(file);

        assertThat(list).extracting(ModelRestaurantImporter.Designation::id).containsExactly("A1", "A4");
        assertThat(list.get(0).designatedOn()).hasToString("2025-12-10");
        assertThat(list.get(1).name()).isEqualTo("줄바꿈 식당");
    }
}
