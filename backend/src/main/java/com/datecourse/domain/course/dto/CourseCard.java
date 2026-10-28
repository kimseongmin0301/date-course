package com.datecourse.domain.course.dto;

import java.util.List;

/** 검색 결과의 코스 하나. matchedKeywords: 고른 키워드 중 이 코스에 있는 것 */
public record CourseCard(long id, String title, String summary, String kind, String sido, String sigungu,
                         double centerLat, double centerLng, Integer totalMinutes, int distanceMeters,
                         List<String> keywords, List<String> matchedKeywords, List<StepSummary> steps) {
}
