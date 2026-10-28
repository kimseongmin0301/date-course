package com.datecourse.domain.place.entity;

import com.datecourse.global.util.LowercaseEnumConverter;

import jakarta.persistence.Converter;

/** 장소를 가져온 곳 (place_source.source) */
public enum PlaceSourceType {
    /** 소상공인시장진흥공단 상가(상권)정보 CSV */
    SBIZ,
    /** 한국관광공사 TourAPI */
    TOUR;

    @Converter(autoApply = true)
    public static class DbConverter extends LowercaseEnumConverter<PlaceSourceType> {
        public DbConverter() {
            super(PlaceSourceType.class);
        }
    }
}
