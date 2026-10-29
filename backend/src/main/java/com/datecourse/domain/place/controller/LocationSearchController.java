package com.datecourse.domain.place.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.datecourse.domain.place.client.NaverLocalSearchClient;
import com.datecourse.domain.place.dto.AddressResult;
import com.datecourse.domain.place.dto.LocationResult;
import com.datecourse.domain.place.exception.PlaceException;
import com.datecourse.domain.place.service.LocationSearchService;
import com.datecourse.domain.place.service.NaverPlaceSearchService;

@RestController
public class LocationSearchController {

    private final LocationSearchService locationSearchService;
    private final NaverPlaceSearchService naverPlaceSearchService;

    public LocationSearchController(LocationSearchService locationSearchService, NaverPlaceSearchService naverPlaceSearchService) {
        this.locationSearchService = locationSearchService;
        this.naverPlaceSearchService = naverPlaceSearchService;
    }

    /** 지역 검색 (지도를 옮길 곳): 지역 이름·명소·주소 최대 10개 */
    @GetMapping("/api/locations")
    public List<LocationResult> search(@RequestParam(name = "q", required = false) String query) {
        return locationSearchService.search(query);
    }

    /** 사진이 없는 장소의 "네이버 이미지 보기": 이미지 검색 섬네일 최대 6장 (가게 공식 사진이 아닐 수 있음) */
    @GetMapping("/api/places/images")
    public List<NaverLocalSearchClient.Image> images(@RequestParam(name = "q", required = false) String query) {
        String q = query == null ? "" : String.join(" ", query.strip().split("\\s+"));
        if (q.length() < 2 || q.length() > 60) {
            throw PlaceException.badQuery();
        }
        return naverPlaceSearchService.images(q);
    }

    /** 주소 찾기 (데이터에 없는 가게를 일정에 직접 넣을 때): 네이버 Geocoding 최대 5개 */
    @GetMapping("/api/addresses")
    public List<AddressResult> addresses(@RequestParam(name = "q", required = false) String query) {
        return locationSearchService.addresses(query);
    }
}
