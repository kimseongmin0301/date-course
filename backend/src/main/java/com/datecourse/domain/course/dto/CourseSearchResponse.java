package com.datecourse.domain.course.dto;

import java.util.List;

/** 코스 검색 결과: 반경 안의 전체 수와 한 쪽(20개) */
public record CourseSearchResponse(int total, int page, int size, List<CourseCard> courses) {
}
