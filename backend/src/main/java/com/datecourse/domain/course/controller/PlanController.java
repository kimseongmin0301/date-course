package com.datecourse.domain.course.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.datecourse.domain.course.dto.PlanResponse;
import com.datecourse.domain.course.dto.PlanSaveRequest;
import com.datecourse.domain.course.dto.PlanSummary;
import com.datecourse.domain.course.exception.CourseException;
import com.datecourse.domain.course.service.PlanService;
import com.datecourse.global.util.VisitorCookie;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 내 일정 저장 (로그인 없이 쿠키 dc_vid 의 방문자 id 로). 저장할 때 쿠키가 없으면 만들어 준다 */
@RestController
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    /** 내가 저장한 일정 목록 (최근 저장 순). 쿠키가 없으면 빈 목록 */
    @GetMapping("/api/plans")
    public List<PlanSummary> list(HttpServletRequest request) {
        return VisitorCookie.read(request).map(planService::list).orElse(List.of());
    }

    /** 이 코스에 저장한 내 일정. 없으면 404 */
    @GetMapping("/api/plans/{courseId}")
    public PlanResponse get(@PathVariable long courseId, HttpServletRequest request) {
        UUID visitor = VisitorCookie.read(request).orElseThrow(CourseException::notFound);
        return planService.get(visitor, courseId);
    }

    @PutMapping("/api/plans/{courseId}")
    public PlanResponse save(@PathVariable long courseId, @RequestBody(required = false) PlanSaveRequest body,
                             HttpServletRequest request, HttpServletResponse response) {
        return planService.save(VisitorCookie.ensure(request, response), courseId, body);
    }

    @DeleteMapping("/api/plans/{courseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long courseId, HttpServletRequest request) {
        UUID visitor = VisitorCookie.read(request).orElseThrow(CourseException::notFound);
        planService.delete(visitor, courseId);
    }
}
