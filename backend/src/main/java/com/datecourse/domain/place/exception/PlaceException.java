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
}
