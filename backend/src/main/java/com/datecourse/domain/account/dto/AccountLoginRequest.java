package com.datecourse.domain.account.dto;

/** 다른 기기에서 로그인: 코드("K7QM-29XD", 줄표·소문자 괜찮음) + 비밀번호 */
public record AccountLoginRequest(String code, String password) {
}
