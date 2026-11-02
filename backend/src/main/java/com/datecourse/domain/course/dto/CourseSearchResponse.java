package com.datecourse.domain.course.dto;

import java.util.List;

/**
 * 코스 검색 결과: 반경 안의 전체 수와 한 쪽(20개).
 * radius: 실제로 찾은 반경. 고른 반경 안에 코스가 하나도 없으면 넓혀 찾고 widened = true (화면은 "넓혀서 찾았어요")
 * foodTotal: 고른 메뉴의 가게가 든 코스 수 (메뉴를 안 골랐으면 null). 0 이면 화면이 "그 메뉴가 든 코스는 없어요"라고 알리고 넣는 방법을 안내
 * shopTotal: 가게 이름으로 찾은 그 가게가 든 코스 수 (가게로 찾지 않았으면 null). 화면은 다른 코스에 "그 가게 넣어 보기"를 붙인다
 */
public record CourseSearchResponse(int total, int page, int size, int radius, boolean widened, List<CourseCard> courses, Integer foodTotal,
                                   Integer shopTotal) {

    public CourseSearchResponse(int total, int page, int size, int radius, boolean widened, List<CourseCard> courses) {
        this(total, page, size, radius, widened, courses, null, null);
    }

    public CourseSearchResponse(int total, int page, int size, int radius, boolean widened, List<CourseCard> courses, Integer foodTotal) {
        this(total, page, size, radius, widened, courses, foodTotal, null);
    }
}
