package com.datecourse.domain.course.dto;

import java.util.List;

/** reasons: 이 장소를 고른 이유 (모범음식점, 관광공사 소개, 체인 아닌 동네 가게 등) */
public record StepDetail(int order, String role, Integer stayMinutes, Integer walkMinutes, String memo, PlaceResponse place,
                         List<String> reasons) {
}
