package com.datecourse.domain.place.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.datecourse.domain.place.entity.Place;
import com.datecourse.domain.place.entity.PlaceMergeCandidate;

public interface PlaceMergeCandidateRepository extends JpaRepository<PlaceMergeCandidate, Long> {

    boolean existsByPlaceAAndPlaceB(Place placeA, Place placeB);
}
