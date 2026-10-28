package com.datecourse.domain.collector.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoTests {

    @Test
    void 위도_0_001도는_약_111m() {
        assertThat(Geo.distanceMeters(37.5, 127.0, 37.501, 127.0)).isCloseTo(111.2, within(0.5));
    }

    @Test
    void 사각형_폭은_반경을_덮는다() {
        double lat = 37.5;
        assertThat(Geo.distanceMeters(lat, 127.0, lat + Geo.latDelta(150), 127.0)).isCloseTo(150, within(1.0));
        assertThat(Geo.distanceMeters(lat, 127.0, lat, 127.0 + Geo.lngDelta(150, lat))).isCloseTo(150, within(1.0));
    }

    @Test
    void 한국_밖_좌표는_거른다() {
        assertThat(Geo.inKorea(37.5, 127.0)).isTrue();
        assertThat(Geo.inKorea(0, 0)).isFalse();
    }
}
