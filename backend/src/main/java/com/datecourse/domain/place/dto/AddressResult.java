package com.datecourse.domain.place.dto;

/** 주소 찾기 결과 (네이버 Geocoding). 일정에 직접 넣을 가게의 좌표 */
public record AddressResult(String roadAddress, String jibunAddress, double lat, double lng) {
}
