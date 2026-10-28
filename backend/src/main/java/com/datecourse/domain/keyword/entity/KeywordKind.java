package com.datecourse.domain.keyword.entity;

import com.datecourse.global.util.LowercaseEnumConverter;

import jakarta.persistence.Converter;

/** 키워드 분류 (keyword.kind) */
public enum KeywordKind {
    /** 분위기: 여러 개 고름 */
    MOOD,
    /** 활동: 여러 개 고름 */
    ACTIVITY,
    /** 실내외: 하나만 */
    SETTING,
    /** 예산(2인): 하나만 */
    BUDGET;

    @Converter(autoApply = true)
    public static class DbConverter extends LowercaseEnumConverter<KeywordKind> {
        public DbConverter() {
            super(KeywordKind.class);
        }
    }
}
