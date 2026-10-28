package com.datecourse.domain.course.dto;

/** 목록 카드·지도 핀에 쓰는 단계 요약 */
public record StepSummary(int order, String role, Integer stayMinutes, Integer walkMinutes,
                          long placeId, String name, String branch, String category, double lat, double lng) {
}
