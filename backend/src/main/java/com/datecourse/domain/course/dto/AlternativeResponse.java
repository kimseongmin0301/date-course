package com.datecourse.domain.course.dto;

import java.util.List;

/** 코스 단계의 근처 대안 하나. distanceMeters: 원래 단계 장소에서의 직선 거리 */
public record AlternativeResponse(PlaceResponse place, List<String> reasons, int distanceMeters) {
}
