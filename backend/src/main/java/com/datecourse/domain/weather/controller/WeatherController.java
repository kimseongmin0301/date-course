package com.datecourse.domain.weather.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.datecourse.domain.weather.dto.WeatherResponse;
import com.datecourse.domain.weather.service.WeatherService;

/** 코스 날짜의 날씨 (코스 상세의 날짜 옆) */
@RestController
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /** lat·lng: 코스 가운데, date: YYYY-MM-DD. 예보가 없으면 available false (오류 아님) */
    @GetMapping("/api/weather")
    public WeatherResponse weather(@RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng,
                                   @RequestParam(required = false) String date) {
        return weatherService.forecast(lat, lng, date);
    }
}
