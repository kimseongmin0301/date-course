package com.datecourse.domain.place.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 같은 장소인지 애매한 쌍 (place_merge_candidate 테이블). placeA 의 id 가 항상 더 작다 */
@Entity
@Table(name = "place_merge_candidate")
public class PlaceMergeCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_a_id")
    private Place placeA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_b_id")
    private Place placeB;

    @Column(name = "distance_m", nullable = false)
    private int distanceM;

    @Column(nullable = false)
    private MergeStatus status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    protected PlaceMergeCandidate() {
    }

    public PlaceMergeCandidate(Place placeA, Place placeB, int distanceM) {
        this.placeA = placeA;
        this.placeB = placeB;
        this.distanceM = distanceM;
        this.status = MergeStatus.PENDING;
    }

    public Long getId() { return id; }
    public Place getPlaceA() { return placeA; }
    public Place getPlaceB() { return placeB; }
    public int getDistanceM() { return distanceM; }
    public MergeStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
}
