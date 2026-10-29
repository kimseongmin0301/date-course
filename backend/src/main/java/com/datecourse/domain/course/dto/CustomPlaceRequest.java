package com.datecourse.domain.course.dto;

/** 데이터에 없어 직접 넣은 가게: 이름(1~30자), 주소(주소 찾기로 고른 것), 좌표 */
public record CustomPlaceRequest(String name, String address, Double lat, Double lng) {
}
