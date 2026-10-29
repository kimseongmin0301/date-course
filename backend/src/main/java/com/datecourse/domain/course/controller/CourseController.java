package com.datecourse.domain.course.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.datecourse.domain.course.dto.AlternativeResponse;
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

    /** 코스 order 번째 단계의 근처 대안 (같은 역할, 좋은 순 5곳). 없는 코스·단계는 404 */
    @GetMapping("/api/courses/{id}/steps/{order}/alternatives")
    public List<AlternativeResponse> alternatives(@PathVariable long id, @PathVariable int order) {
        return courseQueryService.alternatives(id, order);
    }

    /** 코스에 넣을 장소 찾기: (lat, lng) 에서 2km 안, 이름에 q 가 든 곳 가까운 순 10곳 */
    @GetMapping("/api/places/nearby")
    public List<AlternativeResponse> nearbyPlaces(@RequestParam(required = false) Double lat,
                                                  @RequestParam(required = false) Double lng,
                                                  @RequestParam(required = false) String q) {
        return courseQueryService.nearbyPlaces(lat, lng, q);
    }

    /** 코스 상세 (단계별 장소). 없거나 숨긴 코스는 404 */
    /** 네이버 지역 검색으로 가게 찾기 (우리 데이터에 없을 때). area: 검색어 앞에 붙일 동네 (앞 장소의 시군구) */
    @GetMapping("/api/places/naver")
    public List<AlternativeResponse> naverPlaces(@RequestParam(required = false) Double lat,
                                                 @RequestParam(required = false) Double lng,
                                                 @RequestParam(required = false) String area,
                                                 @RequestParam(required = false) String q) {
        return courseQueryService.naverPlaces(lat, lng, area, q);
    }

    @GetMapping("/api/courses/{id}")
    public CourseDetailResponse detail(@PathVariable long id) {
        return courseQueryService.detail(id);
    }
}
