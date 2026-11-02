package com.datecourse.domain.ai.dto;

import java.util.List;

/**
 * AI 추천: 요청 문장 + 지금 검색 조건 + 이미 본 코스(다시 추천할 때 뺌).
 * 검색 조건은 지점 반경(lat·lng·radius) 또는 시도 전체·전국(sido·scene, lat 없이). scene·food(메뉴) 는 둘 다에 붙을 수 있다. shop: 가게 id (지점 반경일 때만),
 * shopName·shopCategory: 가게 이름으로 찾았으면 그 가게 (네이버에서만 찾은 가게는 id 없이). 후보 코스에 그 가게를 넣어 보여주고 고르게 한다
 */
public record AiRecommendRequest(String q, Double lat, Double lng, Integer radius, List<String> kw, List<Long> exclude,
                                 List<String> sido, String scene, String food, Long shop, String shopName, String shopCategory) {
}
