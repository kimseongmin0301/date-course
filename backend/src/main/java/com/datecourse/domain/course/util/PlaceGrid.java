package com.datecourse.domain.course.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.datecourse.domain.collector.util.Geo;

/** 메모리에 올린 장소를 위경도 칸(0.01도, 약 1km)으로 나눠 두고 반경 안을 빨리 찾는다 */
public class PlaceGrid {

    private static final double CELL = 0.01;

    private final Map<Long, List<CoursePlanner.Spot>> cells = new HashMap<>();

    public void add(CoursePlanner.Spot spot) {
        cells.computeIfAbsent(key(cell(spot.lat()), cell(spot.lng())), k -> new ArrayList<>()).add(spot);
    }

    /** 반경 meters 안의 장소 (가까운 순 아님) */
    public List<Near> near(double lat, double lng, double meters) {
        int latFrom = cell(lat - Geo.latDelta(meters));
        int latTo = cell(lat + Geo.latDelta(meters));
        int lngFrom = cell(lng - Geo.lngDelta(meters, lat));
        int lngTo = cell(lng + Geo.lngDelta(meters, lat));
        List<Near> result = new ArrayList<>();
        for (int a = latFrom; a <= latTo; a++) {
            for (int b = lngFrom; b <= lngTo; b++) {
                for (CoursePlanner.Spot spot : cells.getOrDefault(key(a, b), List.of())) {
                    double d = Geo.distanceMeters(lat, lng, spot.lat(), spot.lng());
                    if (d <= meters) {
                        result.add(new Near(spot, d));
                    }
                }
            }
        }
        return result;
    }

    /** 이 위치가 속한 칸 (동네 코스를 칸마다 하나씩 해 볼 때) */
    public static long cellKey(double lat, double lng) {
        return key(cell(lat), cell(lng));
    }

    private static int cell(double degree) {
        return (int) Math.floor(degree / CELL);
    }

    private static long key(int a, int b) {
        return ((long) a << 32) | (b & 0xffffffffL);
    }

    public record Near(CoursePlanner.Spot spot, double meters) {
    }
}
