package com.datecourse.domain.place.dto;

/**
 * 지역 검색 결과 하나. 지도를 옮길 곳.
 * kind: area(시군구·동), place(명소·문화시설), address(Geocoding 주소)
 * radius: 지도에 보여줄 반경(m) 제안
 */
public record LocationResult(String kind, String name, String detail, double lat, double lng, int radius) {
}
