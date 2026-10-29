package com.datecourse.domain.course.dto;

/**
 * 저장할 일정의 단계 하나. role: spot/cafe/meal/mine, stayMinutes: 머무는 시간(분),
 * fixedArrive: 도착 시간을 고정했으면 그날 0시부터 분(없으면 null), origin: course/swap/added, courseOrder: 원래 코스 단계 순서(더한 곳은 null)
 */
public record PlanStepRequest(Long placeId, String role, Integer stayMinutes, Integer fixedArrive, String origin, Integer courseOrder) {
}
