package com.datecourse.domain.course.dto;

import java.util.List;

/** 내 일정 저장. date: YYYY-MM-DD(없어도 됨), start: 출발 HH:MM */
public record PlanSaveRequest(String date, String start, List<PlanStepRequest> steps) {
}
