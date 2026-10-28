package com.datecourse.domain.place.entity;

import com.datecourse.global.util.LowercaseEnumConverter;

import jakarta.persistence.Converter;

/** 장소 키워드를 누가 붙였는지 (place_keyword.origin) */
public enum KeywordOrigin {
    /** 분류로 자동으로 붙임 */
    AUTO,
    /** 관리자가 붙임: 자동 수집이 덮어쓰지 않는다 */
    ADMIN;

    @Converter(autoApply = true)
    public static class DbConverter extends LowercaseEnumConverter<KeywordOrigin> {
        public DbConverter() {
            super(KeywordOrigin.class);
        }
    }
}
