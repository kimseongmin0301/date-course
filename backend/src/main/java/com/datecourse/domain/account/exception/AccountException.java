package com.datecourse.domain.account.exception;

import org.springframework.http.HttpStatus;

import com.datecourse.global.exception.ApiException;

public class AccountException extends ApiException {

    private AccountException(HttpStatus status, String message) {
        super(status, message);
    }

    public static AccountException badPassword(int min, int max) {
        return new AccountException(HttpStatus.BAD_REQUEST, "비밀번호는 " + min + "~" + max + "자로 정해 주세요.");
    }

    public static AccountException alreadyLinked() {
        return new AccountException(HttpStatus.CONFLICT, "이 브라우저에는 이미 코드가 있어요. 내 저장에서 코드를 확인해 주세요.");
    }

    public static AccountException wrong() {
        return new AccountException(HttpStatus.UNAUTHORIZED, "코드나 비밀번호가 맞지 않아요.");
    }

    public static AccountException locked() {
        return new AccountException(HttpStatus.TOO_MANY_REQUESTS, "너무 여러 번 틀렸어요. 15분 뒤에 다시 해 주세요.");
    }
}
