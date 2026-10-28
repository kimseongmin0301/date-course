package com.datecourse.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.datecourse.domain.place.entity.PlaceCategory;

class LowercaseEnumConverterTests {

    final PlaceCategory.DbConverter converter = new PlaceCategory.DbConverter();

    @Test
    void DB_에는_소문자로_저장한다() {
        assertThat(converter.convertToDatabaseColumn(PlaceCategory.RESTAURANT)).isEqualTo("restaurant");
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void DB_의_소문자를_enum_으로_읽는다() {
        assertThat(converter.convertToEntityAttribute("cafe")).isEqualTo(PlaceCategory.CAFE);
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
