package com.datecourse.domain.account.dto;

/** 이 브라우저가 코드로 묶여 있는지 (code 는 4자씩 끊은 "K7QM-29XD", 없으면 null) */
public record AccountStatus(boolean linked, String code) {
}
