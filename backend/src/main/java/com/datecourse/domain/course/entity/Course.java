package com.datecourse.domain.course.entity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.datecourse.domain.keyword.entity.Keyword;
import com.datecourse.domain.place.entity.Place;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** 데이트 코스 (course 테이블). 단계(course_step)와 키워드(course_keyword)를 함께 다룬다 */
@Entity
@Table(name = "course")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String summary;

    @Column(nullable = false)
    private CourseKind kind;

    /** 자동 코스의 중심 장소. 중심 장소마다 자동 코스는 하나 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anchor_place_id")
    private Place anchorPlace;

    @Column(nullable = false)
    private String sido;

    private String sigungu;

    @Column(name = "center_lat", nullable = false)
    private double centerLat;

    @Column(name = "center_lng", nullable = false)
    private double centerLng;

    /** 걸리는 시간(분). 머무는 시간 + 걸어서 옮기는 시간 */
    @Column(name = "total_minutes")
    private Integer totalMinutes;

    /** 예상 예산(원, 2인). 큐레이션 코스만 */
    @Column(name = "budget_min")
    private Integer budgetMin;

    @Column(name = "budget_max")
    private Integer budgetMax;

    /** 순위 점수 캐시 (키워드 일치는 검색 때 더한다) */
    @Column(nullable = false)
    private double score;

    @Column(name = "score_updated_at")
    private OffsetDateTime scoreUpdatedAt;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder")
    private List<CourseStep> steps = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "course_keyword",
            joinColumns = @JoinColumn(name = "course_id"),
            inverseJoinColumns = @JoinColumn(name = "keyword_id"))
    private Set<Keyword> keywords = new HashSet<>();

    protected Course() {
    }

    public Course(String title, CourseKind kind, String sido, String sigungu, double centerLat, double centerLng) {
        this.title = title;
        this.kind = kind;
        this.sido = sido;
        this.sigungu = sigungu;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
    }

    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public CourseKind getKind() { return kind; }
    public Place getAnchorPlace() { return anchorPlace; }
    public String getSido() { return sido; }
    public String getSigungu() { return sigungu; }
    public double getCenterLat() { return centerLat; }
    public double getCenterLng() { return centerLng; }
    public Integer getTotalMinutes() { return totalMinutes; }
    public Integer getBudgetMin() { return budgetMin; }
    public Integer getBudgetMax() { return budgetMax; }
    public double getScore() { return score; }
    public OffsetDateTime getScoreUpdatedAt() { return scoreUpdatedAt; }
    public boolean isHidden() { return hidden; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<CourseStep> getSteps() { return steps; }
    public Set<Keyword> getKeywords() { return keywords; }
}
