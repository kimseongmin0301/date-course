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

/** 장소의 출처별 원본 (place_source 테이블). 한 장소에 출처가 여럿이면 "출처 일치" 점수가 오른다 */
@Entity
@Table(name = "place_source")
public class PlaceSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id")
    private Place place;

    @Column(nullable = false)
    private PlaceSourceType source;

    /** 출처의 id (상가업소번호, contentid) */
    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "raw_name", nullable = false)
    private String rawName;

    /** 마지막으로 수집에서 본 시각. 오래 안 보이면 폐업으로 본다 */
    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    protected PlaceSource() {
    }

    public PlaceSource(Place place, PlaceSourceType source, String externalId, String rawName) {
        this.place = place;
        this.source = source;
        this.externalId = externalId;
        this.rawName = rawName;
        this.lastSeenAt = OffsetDateTime.now();
    }

    /** 수집에서 다시 봤다 (폐업 판단의 기준 시각) */
    public void seen(String rawName) {
        this.rawName = rawName;
        this.lastSeenAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Place getPlace() { return place; }
    public PlaceSourceType getSource() { return source; }
    public String getExternalId() { return externalId; }
    public String getRawName() { return rawName; }
    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
}
