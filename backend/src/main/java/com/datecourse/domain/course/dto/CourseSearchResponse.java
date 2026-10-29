package com.datecourse.domain.course.dto;

import java.util.List;

/**
 * 코스 검색 결과: 반경 안의 전체 수와 한 쪽(20개).
 * radius: 실제로 찾은 반경. 고른 반경 안에 코스가 하나도 없으면 넓혀 찾고 widened = true (화면은 "넓혀서 찾았어요")
 */
public record CourseSearchResponse(int total, int page, int size, int radius, boolean widened, List<CourseCard> courses) {
}
