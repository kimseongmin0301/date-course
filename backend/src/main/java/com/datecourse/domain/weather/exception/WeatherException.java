package com.datecourse.domain.weather.exception;

import org.springframework.http.HttpStatus;

import com.datecourse.global.exception.ApiException;

public class WeatherException extends ApiException {

    private WeatherException(HttpStatus status, String message) {
        super(status, message);
    }

    public static WeatherException badRequest(String message) {
        return new WeatherException(HttpStatus.BAD_REQUEST, message);
    }
}
