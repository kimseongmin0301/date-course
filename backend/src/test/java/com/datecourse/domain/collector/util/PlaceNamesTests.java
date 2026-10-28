package com.datecourse.domain.collector.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlaceNamesTests {

    @Test
    void 정리한_이름은_소문자_글자_숫자만() {
        assertThat(PlaceNames.normalize("Cafe 온 (ON)!")).isEqualTo("cafe온on");
        assertThat(PlaceNames.normalize(" 별마당 도서관 ")).isEqualTo("별마당도서관");
        assertThat(PlaceNames.normalize(null)).isEmpty();
    }

    @Test
    void 끝의_지점명을_뗀다() {
        assertThat(PlaceNames.splitBranch("스타벅스 강남점")).isEqualTo(new PlaceNames.Split("스타벅스", "강남점"));
        assertThat(PlaceNames.splitBranch("스타벅스(강남점)")).isEqualTo(new PlaceNames.Split("스타벅스", "강남점"));
    }

    @Test
    void 지점이_아닌_점은_그대로_둔다() {
        assertThat(PlaceNames.splitBranch("롯데 백화점")).isEqualTo(new PlaceNames.Split("롯데 백화점", null));
        assertThat(PlaceNames.splitBranch("교보문고 서점")).isEqualTo(new PlaceNames.Split("교보문고 서점", null));
        assertThat(PlaceNames.splitBranch("신세계백화점")).isEqualTo(new PlaceNames.Split("신세계백화점", null));
        assertThat(PlaceNames.splitBranch("별마당도서관")).isEqualTo(new PlaceNames.Split("별마당도서관", null));
    }
}
