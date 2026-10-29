package com.datecourse.domain.place.exception;

import org.springframework.http.HttpStatus;

import com.datecourse.global.exception.ApiException;

public class PlaceException extends ApiException {

    private PlaceException(HttpStatus status, String message) {
        super(status, message);
    }

    public static PlaceException badQuery() {
        return new PlaceException(HttpStatus.BAD_REQUEST, "찾을 지역이나 장소를 두 글자 이상 적어 주세요.");
    }

    public static PlaceException badAddress() {
        return new PlaceException(HttpStatus.BAD_REQUEST, "주소를 두 글자 이상 60자 안으로 적어 주세요.");
    }

    /** 네이버 지역 검색 키가 없거나 오늘 한도를 다 씀 */
    public static PlaceException searchUnavailable() {
        return new PlaceException(HttpStatus.SERVICE_UNAVAILABLE, "지금은 네이버에서 찾을 수 없어요. 이름과 주소로 직접 넣어 주세요.");
    }

    /** 네이버 이미지 검색을 못 씀 (키 없음·한도·오류) */
    public static PlaceException imagesUnavailable() {
        return new PlaceException(HttpStatus.SERVICE_UNAVAILABLE, "지금은 네이버 이미지를 불러올 수 없어요. 잠시 뒤에 다시 해 주세요.");
    }

    /** Geocoding 키가 없거나 오늘 한도를 다 씀 */
    public static PlaceException addressUnavailable() {
        return new PlaceException(HttpStatus.SERVICE_UNAVAILABLE, "지금은 주소 찾기를 쓸 수 없어요. 잠시 뒤에 다시 해 주세요.");
    }
}
