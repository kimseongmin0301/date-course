package com.datecourse.domain.course.entity;

import com.datecourse.global.util.LowercaseEnumConverter;

import jakarta.persistence.Converter;

/** 코스를 만든 방식 (course.kind) */
public enum CourseKind {
    /** 관리자가 등록 */
    CURATED,
    /** 중심 장소로 자동 조합 */
    AUTO;

    @Converter(autoApply = true)
    public static class DbConverter extends LowercaseEnumConverter<CourseKind> {
        public DbConverter() {
            super(CourseKind.class);
        }
    }
}
