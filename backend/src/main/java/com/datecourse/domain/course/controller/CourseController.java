package com.datecourse.domain.course.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.datecourse.domain.course.dto.CourseDetailResponse;
import com.datecourse.domain.course.dto.CourseSearchResponse;
import com.datecourse.domain.course.service.CourseQueryService;

@RestController
public class CourseController {

    private final CourseQueryService courseQueryService;

    public CourseController(CourseQueryService courseQueryService) {
        this.courseQueryService = courseQueryService;
    }

    /** 코스 순위 목록: 지도 가운데(lat, lng)에서 radius(m, 기본 3km) 안, 키워드 kw 0~3개 (kw=quiet,romantic 또는 kw=quiet&kw=romantic) */
    @GetMapping("/api/courses")
    public CourseSearchResponse search(@RequestParam(required = false) Double lat,
                                       @RequestParam(required = false) Double lng,
                                       @RequestParam(required = false) Integer radius,
                                       @RequestParam(name = "kw", required = false) List<String> keywords,
                                       @RequestParam(required = false) Integer page) {
        return courseQueryService.search(courseQueryService.normalize(lat, lng, radius, keywords, page));
    }

    /** 코스 상세 (단계별 장소). 없거나 숨긴 코스는 404 */
    @GetMapping("/api/courses/{id}")
    public CourseDetailResponse detail(@PathVariable long id) {
        return courseQueryService.detail(id);
    }
}
