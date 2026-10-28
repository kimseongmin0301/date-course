package com.datecourse.domain.keyword.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 코스·장소에 붙이는 키워드 (keyword 테이블). 목록은 db/V2__seed_keyword.sql */
@Entity
@Table(name = "keyword")
public class Keyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 주소·API 에 쓰는 영문 id (예: quiet) */
    @Column(nullable = false, unique = true)
    private String code;

    /** 화면에 보여줄 이름 (예: 조용) */
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private KeywordKind kind;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Keyword() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public KeywordKind getKind() { return kind; }
    public int getSortOrder() { return sortOrder; }
}
