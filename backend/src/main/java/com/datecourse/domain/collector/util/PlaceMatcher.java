package com.datecourse.domain.collector.util;

import java.util.Objects;

import com.datecourse.domain.place.entity.PlaceCategory;

/**
 * 같은 장소인지 정하는 규칙 (TourAPI·상가정보 수집이 함께 쓴다).
 * 정리한 이름이 같은 장소들 중에서
 *  - 50m 안 → 같은 장소
 *  - 50~150m → 중복 후보 (관리자가 정함)
 *  - 둘 다 지점명이 있는데 다르면 (강남점 ↔ 역삼점) 다른 가게
 *  - 관광명소는 관광명소끼리만 (관광지 "인사동" 옆의 가게 "인사동" 처럼 이름이 지명과 같은 가게가 있어서)
 */
public final class PlaceMatcher {

    public static final double SAME_PLACE_M = 50;
    public static final double CANDIDATE_M = 150;

    private PlaceMatcher() {
    }

    /** 비교할 기존 장소 (DB 에서 읽었든 이번 수집에서 막 만들었든) */
    public record Known(Long id, String branch, PlaceCategory category, double lat, double lng) {
    }

    /** same: 같은 장소(없으면 null). near: 중복 후보(없으면 null) */
    public record Match(Long sameId, Long nearId, double nearDistance) {
        static final Match NONE = new Match(null, null, 0);
    }

    public static Match match(Iterable<Known> sameName, String branch, PlaceCategory category, double lat, double lng) {
        Long same = null;
        Long near = null;
        double sameDistance = Double.MAX_VALUE;
        double nearDistance = Double.MAX_VALUE;
        for (Known k : sameName) {
            if (differentBranch(k.branch(), branch) || !compatible(k.category(), category)) {
                continue;
            }
            double d = Geo.distanceMeters(k.lat(), k.lng(), lat, lng);
            if (d <= SAME_PLACE_M && d < sameDistance) {
                same = k.id();
                sameDistance = d;
            } else if (d <= CANDIDATE_M && d < nearDistance) {
                near = k.id();
                nearDistance = d;
            }
        }
        if (same == null && near == null) {
            return Match.NONE;
        }
        return new Match(same, same != null ? null : near, nearDistance);
    }

    /** 관광명소는 관광명소끼리만. 나머지(문화시설 ↔ 서점·갤러리, 음식점 ↔ 카페 등)는 출처마다 분류가 달라도 같은 곳일 수 있다 */
    static boolean compatible(PlaceCategory a, PlaceCategory b) {
        return (a == PlaceCategory.ATTRACTION) == (b == PlaceCategory.ATTRACTION);
    }

    public static boolean differentBranch(String a, String b) {
        return a != null && b != null && !Objects.equals(PlaceNames.normalize(a), PlaceNames.normalize(b));
    }
}
