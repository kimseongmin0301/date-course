package com.datecourse.domain.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.datecourse.domain.course.entity.Course;

public interface CourseRepository extends JpaRepository<Course, Long> {
}
