package com.datecourse.global.exception;

import org.springframework.http.HttpStatus;

/** 사용자에게 보여줄 오류. message 는 화면에 그대로 표시되므로 한국어 문장으로 쓴다. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
