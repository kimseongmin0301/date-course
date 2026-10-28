package com.datecourse.domain.place.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.datecourse.domain.place.dto.LocationResult;
import com.datecourse.domain.place.service.LocationSearchService;

@RestController
public class LocationSearchController {

    private final LocationSearchService locationSearchService;

    public LocationSearchController(LocationSearchService locationSearchService) {
        this.locationSearchService = locationSearchService;
    }

    /** 지역 검색 (지도를 옮길 곳): 지역 이름·명소·주소 최대 10개 */
    @GetMapping("/api/locations")
    public List<LocationResult> search(@RequestParam(name = "q", required = false) String query) {
        return locationSearchService.search(query);
    }
}
