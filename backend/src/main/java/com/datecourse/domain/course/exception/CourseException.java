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

    public static CourseException tooMany(String message) {
        return new CourseException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    /** 공유 링크가 없거나 기간(90일)이 지남 */
    public static CourseException shareNotFound() {
        return new CourseException(HttpStatus.NOT_FOUND, "공유 링크가 없거나 기간이 지났어요.");
    }
}
