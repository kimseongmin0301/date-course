package com.datecourse.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.datecourse.domain.place.client.NaverGeocodeClient;
import com.datecourse.domain.place.dto.LocationResult;
import com.datecourse.domain.place.exception.PlaceException;
import com.datecourse.domain.place.repository.LocationSearchRepository;
import com.datecourse.global.cache.JsonCache;

class LocationSearchServiceTests {

    LocationSearchRepository repository = mock(LocationSearchRepository.class);
    NaverGeocodeClient geocoder = mock(NaverGeocodeClient.class);
    JsonCache cache = mock(JsonCache.class);
    LocationSearchService service = new LocationSearchService(repository, geocoder, cache, 2);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        given(cache.getOrLoad(any(), any(), any(), any())).willAnswer(inv -> ((Supplier<Object>) inv.getArgument(3)).get());
        given(geocoder.configured()).willReturn(true);
    }

    @Test
    void 끝의_동_구를_떼서_지역을_찾는다() {
        assertThat(LocationSearchService.areaPrefix("성수동")).isEqualTo("성수");
        assertThat(LocationSearchService.areaPrefix("강남구")).isEqualTo("강남");
        assertThat(LocationSearchService.areaPrefix("중구")).isEqualTo("중구");
        assertThat(LocationSearchService.areaPrefix("별마당도서관")).isEqualTo("별마당도서관");
    }

    @Test
    void 너무_짧거나_긴_검색어는_400() {
        assertThatThrownBy(() -> LocationSearchService.clean(" 강 ")).isInstanceOf(PlaceException.class);
        assertThatThrownBy(() -> LocationSearchService.clean(null)).isInstanceOf(PlaceException.class);
        assertThatThrownBy(() -> LocationSearchService.clean("가".repeat(41))).isInstanceOf(PlaceException.class);
        assertThat(LocationSearchService.clean("  강남대로   396 ")).isEqualTo("강남대로 396");
    }

    @Test
    void 지역_명소_주소_순서로_모은다() {
        given(repository.areas(eq("성수"), anyInt())).willReturn(List.of(new LocationResult("area", "성수1가1동", "서울특별시 성동구", 37.54, 127.05, 1500)));
        given(repository.landmarks(eq("성수동"), anyInt())).willReturn(List.of(new LocationResult("place", "성수동 카페거리", "서울", 37.54, 127.05, 1500)));
        given(cache.increment(anyString(), any())).willReturn(1L);
        given(geocoder.geocode("성수동")).willReturn(List.of(new NaverGeocodeClient.Address("", "서울특별시 성동구 성수동1가", 37.54, 127.04)));

        List<LocationResult> result = service.search("성수동");

        assertThat(result).extracting(LocationResult::kind).containsExactly("area", "place", "address");
        assertThat(result.get(2).name()).isEqualTo("서울특별시 성동구 성수동1가");
    }

    @Test
    void 하루_한도를_넘으면_Geocoding_을_부르지_않는다() {
        given(cache.increment(anyString(), any())).willReturn(3L);
        service.search("강남대로 396");
        verify(geocoder, never()).geocode(anyString());
    }

    @Test
    void Redis_가_안_되면_Geocoding_을_부르지_않는다() {
        given(cache.increment(anyString(), any())).willReturn(-1L);
        service.search("강남대로 396");
        verify(geocoder, never()).geocode(anyString());
    }
}
