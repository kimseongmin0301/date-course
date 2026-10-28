package com.datecourse.domain.place.entity;

import com.datecourse.global.util.LowercaseEnumConverter;

import jakarta.persistence.Converter;

/** 중복 후보를 관리자가 어떻게 정했는지 (place_merge_candidate.status) */
public enum MergeStatus {
    /** 아직 안 봄 */
    PENDING,
    /** 같은 장소라 합침 */
    MERGED,
    /** 다른 장소 */
    DIFFERENT;

    @Converter(autoApply = true)
    public static class DbConverter extends LowercaseEnumConverter<MergeStatus> {
        public DbConverter() {
            super(MergeStatus.class);
        }
    }
}
