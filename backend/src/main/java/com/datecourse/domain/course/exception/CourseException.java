package com.datecourse.domain.course.exception;

import org.springframework.http.HttpStatus;

import com.datecourse.global.exception.ApiException;

public class CourseException extends ApiException {

    private CourseException(HttpStatus status, String message) {
        super(status, message);
    }

    public static CourseException notFound() {
        return new CourseException(HttpStatus.NOT_FOUND, "코스를 찾을 수 없어요.");
    }

    public static CourseException badRequest(String message) {
        return new CourseException(HttpStatus.BAD_REQUEST, message);
    }
}
