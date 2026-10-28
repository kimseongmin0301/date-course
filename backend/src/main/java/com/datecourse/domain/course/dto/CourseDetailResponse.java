package com.datecourse.domain.course.dto;

import java.util.List;

/** 코스 상세 (동선 지도, 단계별 장소 카드) */
public record CourseDetailResponse(long id, String title, String summary, String kind, String sido, String sigungu,
                                   double centerLat, double centerLng, Integer totalMinutes, Integer budgetMin, Integer budgetMax,
                                   List<String> keywords, List<StepDetail> steps) {
}
