package com.datecourse.domain.ai.exception;

import org.springframework.http.HttpStatus;

import com.datecourse.global.exception.ApiException;

public class AiException extends ApiException {

    private AiException(HttpStatus status, String message) {
        super(status, message);
    }

    public static AiException badQuery(int min, int max) {
        return new AiException(HttpStatus.BAD_REQUEST, "원하는 데이트를 " + min + "~" + max + "자로 적어 주세요.");
    }

    public static AiException tooMany() {
        return new AiException(HttpStatus.TOO_MANY_REQUESTS, "AI 추천을 너무 자주 눌렀어요. 잠시 뒤에 다시 해 주세요.");
    }
}
