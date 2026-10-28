package com.datecourse.domain.collector.util;

/** 위경도 계산 (PostGIS 없이) */
public final class Geo {

    private static final double EARTH_RADIUS_M = 6_371_000;
    private static final double METERS_PER_DEGREE_LAT = 111_320;

    private Geo() {
    }

    /** 두 점 사이 거리(m), 하버사인 */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    /** 반경 meters 를 덮는 위도 폭(도) */
    public static double latDelta(double meters) {
        return meters / METERS_PER_DEGREE_LAT;
    }

    /** 반경 meters 를 덮는 경도 폭(도). 위도가 높을수록 넓어진다 */
    public static double lngDelta(double meters, double lat) {
        return meters / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(lat)));
    }

    /** DB 의 CHECK 와 같은 범위 (한국 안) */
    public static boolean inKorea(double lat, double lng) {
        return lat >= 33 && lat <= 39 && lng >= 124 && lng <= 132;
    }
}
