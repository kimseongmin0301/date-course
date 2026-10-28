package com.datecourse.domain.place.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.datecourse.domain.place.entity.PlaceSource;
import com.datecourse.domain.place.entity.PlaceSourceType;

public interface PlaceSourceRepository extends JpaRepository<PlaceSource, Long> {

    /** 수집할 때 이미 넣은 원본인지 확인 */
    Optional<PlaceSource> findBySourceAndExternalId(PlaceSourceType source, String externalId);
}
