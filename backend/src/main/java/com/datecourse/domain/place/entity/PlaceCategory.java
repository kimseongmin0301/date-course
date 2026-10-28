package com.datecourse.domain.place.entity;

import com.datecourse.global.util.LowercaseEnumConverter;

import jakarta.persistence.Converter;

/** 우리 장소 분류 (place.category). 출처마다 다른 업종 이름을 이 분류로 모은다 */
public enum PlaceCategory {
    /** 관광명소 */
    ATTRACTION,
    /** 전시·공연 등 문화시설 */
    CULTURE,
    /** 체험·놀거리 */
    ACTIVITY,
    /** 소품샵 등 구경할 가게 */
    SHOP,
    CAFE,
    RESTAURANT,
    BAR;

    @Converter(autoApply = true)
    public static class DbConverter extends LowercaseEnumConverter<PlaceCategory> {
        public DbConverter() {
            super(PlaceCategory.class);
        }
    }
}
