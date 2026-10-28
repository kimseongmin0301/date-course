package com.datecourse.domain.course.dto;

public record StepDetail(int order, String role, Integer stayMinutes, Integer walkMinutes, String memo, PlaceResponse place) {
}
