package com.datecourse.domain.collector.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvLineTests {

    @Test
    void 쉼표로_나눈다() {
        assertThat(CsvLine.parse("a,b,,c")).containsExactly("a", "b", "", "c");
    }

    @Test
    void 따옴표_안의_쉼표와_겹따옴표() {
        assertThat(CsvLine.parse("\"카페, 온\",\"말하길 \"\"안녕\"\"\",x")).containsExactly("카페, 온", "말하길 \"안녕\"", "x");
    }
}
