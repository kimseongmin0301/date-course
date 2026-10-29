package com.datecourse.domain.course.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.datecourse.domain.course.dto.CourseCard;
import com.datecourse.domain.course.dto.CourseSearchRequest;
import com.datecourse.domain.course.dto.CourseSearchResponse;
import com.datecourse.domain.course.exception.CourseException;
import com.datecourse.domain.course.service.CourseQueryService;

@WebMvcTest(CourseController.class)
class CourseControllerTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CourseQueryService courseQueryService;

    @Test
    void 코스_순위_목록() throws Exception {
        CourseSearchRequest req = new CourseSearchRequest(37.5, 127.0, 3000, List.of("quiet"), 0);
        given(courseQueryService.normalize(37.5, 127.0, null, List.of("quiet"), null)).willReturn(req);
        given(courseQueryService.search(req)).willReturn(new CourseSearchResponse(1, 0, 20, 3000, false, List.of(
                new CourseCard(7, "미술관 데이트 코스", "미술관 → 카페", "auto", "서울특별시", "종로구", 37.5, 127.0, 200, 120,
                        List.of("quiet"), List.of("quiet"), List.of()))));

        mvc.perform(get("/api/courses").param("lat", "37.5").param("lng", "127.0").param("kw", "quiet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.courses[0].title").value("미술관 데이트 코스"))
                .andExpect(jsonPath("$.courses[0].matchedKeywords[0]").value("quiet"));
    }

    @Test
    void 잘못된_입력은_메시지와_400() throws Exception {
        given(courseQueryService.normalize(any(), any(), any(), any(), any()))
                .willThrow(CourseException.badRequest("지도에서 국내 지역을 골라 주세요."));

        mvc.perform(get("/api/courses"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지도에서 국내 지역을 골라 주세요."));
    }

    @Test
    void 없는_코스는_404() throws Exception {
        given(courseQueryService.detail(99)).willThrow(CourseException.notFound());

        mvc.perform(get("/api/courses/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("코스를 찾을 수 없어요."));
    }
}
