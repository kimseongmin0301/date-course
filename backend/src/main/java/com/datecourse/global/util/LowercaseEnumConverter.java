package com.datecourse.global.util;

import jakarta.persistence.AttributeConverter;

/**
 * enum 을 DB 에 소문자로 저장한다 (CHECK 제약이 소문자 값). 각 enum 안에서
 * {@code @Converter(autoApply = true) static class DbConverter extends LowercaseEnumConverter<…>} 로 쓴다.
 */
public abstract class LowercaseEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected LowercaseEnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E value) {
        return value == null ? null : value.name().toLowerCase();
    }

    @Override
    public E convertToEntityAttribute(String value) {
        return value == null ? null : Enum.valueOf(type, value.toUpperCase());
    }
}
