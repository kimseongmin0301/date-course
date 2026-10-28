package com.datecourse.domain.keyword.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.datecourse.domain.keyword.dto.KeywordResponse;
import com.datecourse.domain.keyword.service.KeywordService;

@RestController
public class KeywordController {

    private final KeywordService keywordService;

    public KeywordController(KeywordService keywordService) {
        this.keywordService = keywordService;
    }

    /** 키워드 칩 목록 (분류별 순서) */
    @GetMapping("/api/keywords")
    public List<KeywordResponse> keywords() {
        return keywordService.all();
    }
}
