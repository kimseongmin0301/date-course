package com.datecourse.domain.course.dto;

import java.util.List;

/**
 * 코스 검색 조건 (지도 가운데 + 반경 + 키워드).
 * 캐시 키가 잘게 갈리지 않게 좌표는 소수 셋째 자리(약 100m)로, 키워드는 정렬해서 쓴다.
 */
public record CourseSearchRequest(double lat, double lng, int radius, List<String> keywords, int page) {

    public static final int PAGE_SIZE = 20;

    public double latKey() {
        return Math.round(lat * 1000) / 1000.0;
    }

    public double lngKey() {
        return Math.round(lng * 1000) / 1000.0;
    }

    public String cacheKey() {
        return "courses:search:" + latKey() + ":" + lngKey() + ":" + radius + ":" + String.join(",", keywords) + ":" + page;
    }
}
