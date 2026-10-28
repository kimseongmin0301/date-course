package com.datecourse.domain.place.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.datecourse.domain.place.entity.Place;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    /** 중복 찾기: 정리한 이름이 같고 위경도 사각형 안에 있는 장소 (place_name_idx 로 찾고 좌표로 거른다) */
    List<Place> findByNormalizedNameAndLatBetweenAndLngBetween(String normalizedName,
                                                              double latFrom, double latTo,
                                                              double lngFrom, double lngTo);
}
