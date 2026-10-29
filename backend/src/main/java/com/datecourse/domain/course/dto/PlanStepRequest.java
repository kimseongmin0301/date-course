package com.datecourse.domain.course.dto;

/**
 * 저장할 일정의 단계 하나. role: spot/cafe/meal/mine, stayMinutes: 머무는 시간(분),
 * fixedArrive: 도착 시간을 고정했으면 그날 0시부터 분(없으면 null), origin: course/swap/added, courseOrder: 원래 코스 단계 순서(더한 곳은 null),
 * custom: 데이터에 없어 직접 넣은 가게 (이때 placeId 는 null, role 은 mine)
 */
public record PlanStepRequest(Long placeId, String role, Integer stayMinutes, Integer fixedArrive, String origin, Integer courseOrder,
                              CustomPlaceRequest custom) {

    public PlanStepRequest(Long placeId, String role, Integer stayMinutes, Integer fixedArrive, String origin, Integer courseOrder) {
        this(placeId, role, stayMinutes, fixedArrive, origin, courseOrder, null);
    }
}
