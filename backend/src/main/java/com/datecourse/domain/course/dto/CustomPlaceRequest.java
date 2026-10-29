package com.datecourse.domain.course.dto;

/**
 * 데이터에 없어 직접 넣은 가게: 이름(1~30자), 주소(주소 찾기로 고른 것), 좌표.
 * source: user(이름·주소로 직접) / naver(네이버 지역 검색에서 고름, 이용자 제안으로 모으지 않음). 없으면 user
 */
public record CustomPlaceRequest(String name, String address, Double lat, Double lng, String source) {

    public CustomPlaceRequest(String name, String address, Double lat, Double lng) {
        this(name, address, lat, lng, null);
    }
}
