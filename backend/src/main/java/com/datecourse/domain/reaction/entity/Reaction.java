package com.datecourse.domain.reaction.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.datecourse.domain.course.entity.Course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 코스 반응 하나 (reaction 테이블). 한 방문자는 코스마다 종류별로 한 번만 */
@Entity
@Table(name = "reaction")
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    /** 방문자 id (브라우저마다 만드는 UUID). 다른 방문자에게 내보내지 않는다 */
    @Column(name = "visitor_id", nullable = false)
    private UUID visitorId;

    @Column(nullable = false)
    private ReactionKind kind;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Reaction() {
    }

    public Reaction(Course course, UUID visitorId, ReactionKind kind) {
        this.course = course;
        this.visitorId = visitorId;
        this.kind = kind;
    }

    public Long getId() { return id; }
    public Course getCourse() { return course; }
    public UUID getVisitorId() { return visitorId; }
    public ReactionKind getKind() { return kind; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
