package com.datecourse.domain.ai.dto;

import java.util.List;

/** AI 추천: 요청 문장 + 지금 검색 조건(지도 가운데·반경·키워드) + 이미 본 코스(다시 추천할 때 뺌) */
public record AiRecommendRequest(String q, Double lat, Double lng, Integer radius, List<String> kw, List<Long> exclude) {
}
