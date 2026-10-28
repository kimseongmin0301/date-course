package com.datecourse.domain.keyword.dto;

import com.datecourse.domain.keyword.entity.Keyword;

/** 화면의 키워드 칩 하나. kind: mood·activity·setting·budget */
public record KeywordResponse(String code, String name, String kind) {

    public static KeywordResponse from(Keyword keyword) {
        return new KeywordResponse(keyword.getCode(), keyword.getName(), keyword.getKind().name().toLowerCase());
    }
}
