package com.datecourse.domain.course.dto;

import java.util.List;

/** 저장한 일정의 단계 (장소 정보는 지금 DB 의 것으로 이어 붙임) */
public record PlanStepResponse(String role, int stayMinutes, Integer fixedArrive, String origin, Integer courseOrder,
                               PlaceResponse place, List<String> reasons) {
}
