package com.datecourse.domain.collector.dto;

import com.datecourse.domain.place.entity.PlaceCategory;
import com.datecourse.domain.place.entity.PlaceSourceType;

/** 출처 한 곳에서 읽은 장소 하나. 수집기가 이것을 place·place_source 에 넣는다 */
public record PlaceCandidate(
        PlaceSourceType source,
        String externalId,
        String rawName,
        String name,
        String branch,
        PlaceCategory category,
        String detailCategory,
        double lat,
        double lng,
        String sido,
        String sigungu,
        String dong,
        String roadAddress,
        String lotAddress,
        String phone,
        String imageUrl,
        String description) {
}
