package com.datecourse.domain.course.entity;

import com.datecourse.global.util.LowercaseEnumConverter;

import jakarta.persistence.Converter;

/** 코스 단계의 역할 (course_step.role). 근처 대안을 찾을 때 같은 역할로 찾는다 */
public enum StepRole {
    /** 구경·놀거리 */
    SPOT,
    CAFE,
    /** 식사 */
    MEAL;

    @Converter(autoApply = true)
    public static class DbConverter extends LowercaseEnumConverter<StepRole> {
        public DbConverter() {
            super(StepRole.class);
        }
    }
}
