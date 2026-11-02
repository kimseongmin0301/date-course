package com.datecourse.domain.ai.dto;

import java.util.List;

import com.datecourse.domain.place.dto.LocationResult;

/**
 * 말로 찾기 결과: 알아들은 지역(없으면 null: 지금 지도에서), 키워드 코드(실내외·예산 포함 3개까지),
 * aiUsed: 규칙으로 모자라 AI 가 거든 부분이 있는지, understood: 화면에 보여줄 "성수동 · 조용 · 실내"
 */
public record AiParseResponse(LocationResult area, List<String> keywords, boolean aiUsed, String understood) {
}
