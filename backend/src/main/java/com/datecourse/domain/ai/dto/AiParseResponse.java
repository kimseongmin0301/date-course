package com.datecourse.domain.ai.dto;

import java.util.List;

import com.datecourse.domain.place.dto.LocationResult;

/**
 * 말로 찾기 결과: 알아들은 지역(지점, 없으면 null), 키워드 코드(실내외·예산 포함 3개까지),
 * aiUsed: 규칙으로 모자라 AI 가 거든 부분이 있는지, understood: 화면에 보여줄 "부산 · 바다 · 비 오는 날",
 * sidos: 시도 범위("부산" → 부산광역시, "전라도" → 둘, 비면 없음), scene: 풍경(sea·mountain·water), rainy: 비·눈 오는 날.
 * 화면: area 가 있으면 그 지점 반경, 없고 sidos·scene 이 있으면 시도 전체·전국(/api/courses/region), 둘 다 없으면 지금 지도
 */
public record AiParseResponse(LocationResult area, List<String> keywords, boolean aiUsed, String understood,
                              List<String> sidos, String scene, boolean rainy) {
}
