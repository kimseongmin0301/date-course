package com.datecourse.domain.place.dto;

/**
 * 지역 검색 결과 하나. 지도를 옮길 곳.
 * kind: area(시군구·동), station(역: 역 이름을 붙인 가게들의 평균 위치), place(명소·문화시설), address(Geocoding 주소),
 *       named(상권·건물 이름: 네이버 지역 검색에서 그 이름이 든 가게 자리),
 *       shop(가게: 이름이 똑같은 카페·식당·술집·소품샵·놀거리. 그 가게가 든 코스를 위로, 다른 코스에는 넣어 보기)
 * radius: 지도에 보여줄 반경(m) 제안
 * placeId: shop 이 우리 데이터의 가게면 그 id (네이버에서만 찾은 가게는 null), category: shop 의 분류 (cafe·restaurant·bar·shop·activity)
 */
public record LocationResult(String kind, String name, String detail, double lat, double lng, int radius, Long placeId, String category) {

    public LocationResult(String kind, String name, String detail, double lat, double lng, int radius) {
        this(kind, name, detail, lat, lng, radius, null, null);
    }
}
