package com.datecourse.domain.weather.dto;

/**
 * 그날 날씨 (코스 상세의 날짜 옆). available: 예보가 있으면 true (16일보다 먼 날·지난 날·예보를 못 받았으면 false),
 * label·emoji: "비"·🌧, rainy: 비·눈이 오거나 비 올 확률 60% 이상 (화면이 야외 코스면 실내 코스를 권함)
 */
public record WeatherResponse(boolean available, String date, String label, String emoji, Integer rainProbability,
                              Integer tempMin, Integer tempMax, boolean rainy) {

    public static WeatherResponse none(String date) {
        return new WeatherResponse(false, date, null, null, null, null, null, false);
    }
}
