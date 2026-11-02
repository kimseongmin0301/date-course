package com.datecourse.domain.ai.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.datecourse.domain.ai.dto.AiParseResponse;
import com.datecourse.domain.ai.dto.AiRecommendRequest;
import com.datecourse.domain.ai.dto.AiRecommendResponse;
import com.datecourse.domain.ai.service.AiParseService;
import com.datecourse.domain.ai.service.AiRecommendService;
import com.datecourse.global.util.VisitorCookie;

import jakarta.servlet.http.HttpServletRequest;

/** 실험 기능: 말로 찾기(문장 → 지역·키워드), AI 추천(후보 코스 안에서 고르기). 서버 안 Ollama 만 쓴다 */
@RestController
public class AiController {

    private final AiParseService parseService;
    private final AiRecommendService recommendService;

    public AiController(AiParseService parseService, AiRecommendService recommendService) {
        this.parseService = parseService;
        this.recommendService = recommendService;
    }

    /** 요청 본문 { q } */
    public record ParseRequest(String q) {
    }

    @PostMapping("/api/ai/parse")
    public AiParseResponse parse(@RequestBody(required = false) ParseRequest body, HttpServletRequest request) {
        return parseService.parse(body == null ? null : body.q(), VisitorCookie.clientIp(request));
    }

    @PostMapping("/api/ai/recommend")
    public AiRecommendResponse recommend(@RequestBody(required = false) AiRecommendRequest body, HttpServletRequest request) {
        return recommendService.recommend(body, VisitorCookie.clientIp(request));
    }
}
