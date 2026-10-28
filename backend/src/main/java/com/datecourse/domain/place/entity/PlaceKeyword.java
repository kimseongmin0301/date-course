package com.datecourse.domain.place.entity;

import java.io.Serializable;
import java.util.Objects;

import com.datecourse.domain.keyword.entity.Keyword;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/** 장소에 붙은 키워드 (place_keyword 테이블) */
@Entity
@Table(name = "place_keyword")
public class PlaceKeyword {

    @EmbeddedId
    private Id id;

    @MapsId("placeId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    @MapsId("keywordId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keyword_id")
    private Keyword keyword;

    @Column(nullable = false)
    private KeywordOrigin origin;

    protected PlaceKeyword() {
    }

    public PlaceKeyword(Place place, Keyword keyword, KeywordOrigin origin) {
        this.id = new Id(place.getId(), keyword.getId());
        this.place = place;
        this.keyword = keyword;
        this.origin = origin;
    }

    public Place getPlace() { return place; }
    public Keyword getKeyword() { return keyword; }
    public KeywordOrigin getOrigin() { return origin; }

    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "place_id")
        private Long placeId;

        @Column(name = "keyword_id")
        private Long keywordId;

        protected Id() {
        }

        public Id(Long placeId, Long keywordId) {
            this.placeId = placeId;
            this.keywordId = keywordId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Id other && Objects.equals(placeId, other.placeId) && Objects.equals(keywordId, other.keywordId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(placeId, keywordId);
        }
    }
}
