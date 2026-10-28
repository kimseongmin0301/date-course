package com.datecourse.domain.place.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** 장소 하나 (place 테이블). 여러 출처에서 모아 같은 장소는 하나로 합친다 */
@Entity
@Table(name = "place")
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String branch;

    /** 중복 찾기용 이름: 공백·괄호·기호를 빼고 소문자로 */
    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @Column(nullable = false)
    private PlaceCategory category;

    /** 출처의 원래 업종·분류 이름 (예: 커피전문점) */
    @Column(name = "detail_category")
    private String detailCategory;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(nullable = false)
    private String sido;

    private String sigungu;

    private String dong;

    @Column(name = "road_address")
    private String roadAddress;

    @Column(name = "lot_address")
    private String lotAddress;

    private String phone;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(columnDefinition = "text")
    private String description;

    /** 폐업·사라짐으로 본 시각. 영업 중이면 null */
    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private OffsetDateTime updatedAt;

    protected Place() {
    }

    public Place(String name, String branch, String normalizedName, PlaceCategory category, String detailCategory,
                 double lat, double lng, String sido, String sigungu, String dong) {
        this.name = name;
        this.branch = branch;
        this.normalizedName = normalizedName;
        this.category = category;
        this.detailCategory = detailCategory;
        this.lat = lat;
        this.lng = lng;
        this.sido = sido;
        this.sigungu = sigungu;
        this.dong = dong;
    }

    /** 다른 출처에서 같은 장소를 다시 봤을 때: 비어 있는 칸만 채우고, 폐업으로 봤던 장소는 되살린다 */
    public void fillMissing(String roadAddress, String lotAddress, String phone, String imageUrl, String description) {
        this.roadAddress = firstNonBlank(this.roadAddress, roadAddress);
        this.lotAddress = firstNonBlank(this.lotAddress, lotAddress);
        this.phone = firstNonBlank(this.phone, phone);
        this.imageUrl = firstNonBlank(this.imageUrl, imageUrl);
        this.description = firstNonBlank(this.description, description);
        this.closedAt = null;
    }

    private static String firstNonBlank(String current, String incoming) {
        return current != null && !current.isBlank() ? current : (incoming == null || incoming.isBlank() ? current : incoming);
    }

    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getBranch() { return branch; }
    public String getNormalizedName() { return normalizedName; }
    public PlaceCategory getCategory() { return category; }
    public String getDetailCategory() { return detailCategory; }
    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public String getSido() { return sido; }
    public String getSigungu() { return sigungu; }
    public String getDong() { return dong; }
    public String getRoadAddress() { return roadAddress; }
    public String getLotAddress() { return lotAddress; }
    public String getPhone() { return phone; }
    public String getImageUrl() { return imageUrl; }
    public String getDescription() { return description; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
