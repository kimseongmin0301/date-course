package com.datecourse.domain.place.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.datecourse.domain.place.entity.Place;

public interface PlaceRepository extends JpaRepository<Place, Long> {
}
