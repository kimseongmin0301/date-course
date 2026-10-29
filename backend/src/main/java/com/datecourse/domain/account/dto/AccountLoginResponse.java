package com.datecourse.domain.account.dto;

/** 로그인 결과: 코드, 이 브라우저에서 계정으로 옮긴 일정 수 */
public record AccountLoginResponse(String code, int moved) {
}
