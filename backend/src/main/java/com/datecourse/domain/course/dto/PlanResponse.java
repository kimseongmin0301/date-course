package com.datecourse.domain.course.dto;

import java.util.List;

/** 저장한 내 일정 하나 */
public record PlanResponse(long courseId, String title, String date, String start, String savedAt, List<PlanStepResponse> steps) {
}
