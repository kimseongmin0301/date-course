package com.datecourse.domain.course.dto;

/** 코스 상세의 장소. naverMapUrl: 네이버지도 검색 주소 (API 없이 이름·시군구로 검색) */
public record PlaceResponse(long id, String name, String branch, String category, String detailCategory,
                            double lat, double lng, String sido, String sigungu, String roadAddress, String lotAddress,
                            String phone, String imageUrl, String description, String naverMapUrl) {
}
