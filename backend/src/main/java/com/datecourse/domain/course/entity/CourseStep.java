package com.datecourse.domain.course.entity;

import com.datecourse.domain.place.entity.Place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 코스의 단계 하나 (course_step 테이블). 순서대로 방문하는 장소 */
@Entity
@Table(name = "course_step")
public class CourseStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    /** 1부터 */
    @Column(name = "step_order", nullable = false)
    private short stepOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id")
    private Place place;

    @Column(nullable = false)
    private StepRole role;

    /** 머무는 시간(분) */
    @Column(name = "stay_minutes")
    private Integer stayMinutes;

    /** 앞 단계에서 걸어서 오는 시간(분). 첫 단계는 null */
    @Column(name = "walk_minutes")
    private Integer walkMinutes;

    /** 관리자 한 줄 설명 */
    private String memo;

    protected CourseStep() {
    }

    public CourseStep(Course course, short stepOrder, Place place, StepRole role) {
        this.course = course;
        this.stepOrder = stepOrder;
        this.place = place;
        this.role = role;
    }

    public Long getId() { return id; }
    public Course getCourse() { return course; }
    public short getStepOrder() { return stepOrder; }
    public Place getPlace() { return place; }
    public StepRole getRole() { return role; }
    public Integer getStayMinutes() { return stayMinutes; }
    public Integer getWalkMinutes() { return walkMinutes; }
    public String getMemo() { return memo; }
}
