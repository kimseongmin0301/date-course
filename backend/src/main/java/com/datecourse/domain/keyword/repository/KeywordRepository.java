package com.datecourse.domain.keyword.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.datecourse.domain.keyword.entity.Keyword;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {

    /** 화면의 키워드 칩 순서 (분류별, sort_order 순) */
    List<Keyword> findAllByOrderByKindAscSortOrderAsc();

    Optional<Keyword> findByCode(String code);
}
