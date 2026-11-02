package com.datecourse.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.datecourse.domain.place.client.NaverGeocodeClient;
import com.datecourse.domain.place.client.NaverLocalSearchClient;
import com.datecourse.domain.place.dto.AddressResult;
import com.datecourse.domain.place.dto.LocationResult;
import com.datecourse.domain.place.exception.PlaceException;
import com.datecourse.domain.place.repository.LocationSearchRepository;
import com.datecourse.global.cache.JsonCache;

class LocationSearchServiceTests {

    LocationSearchRepository repository = mock(LocationSearchRepository.class);
    NaverGeocodeClient geocoder = mock(NaverGeocodeClient.class);
    JsonCache cache = mock(JsonCache.class);
    NaverPlaceSearchService naver = mock(NaverPlaceSearchService.class);
    LocationSearchService service = new LocationSearchService(repository, geocoder, cache, naver, 2);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        given(cache.getOrLoad(any(), any(java.util.function.Function.class), any(), any())).willAnswer(inv -> ((Supplier<Object>) inv.getArgument(3)).get());
        given(geocoder.configured()).willReturn(true);
    }

    @Test
    void 끝의_동_구를_떼서_지역을_찾는다() {
        assertThat(LocationSearchService.areaPrefix("성수동")).isEqualTo("성수");
        assertThat(LocationSearchService.areaPrefix("강남구")).isEqualTo("강남");
        assertThat(LocationSearchService.areaPrefix("중구")).isEqualTo("중구");
        assertThat(LocationSearchService.areaPrefix("별마당도서관")).isEqualTo("별마당도서관");
        assertThat(LocationSearchService.areaPrefix("반석역")).isEqualTo("반석");
    }

    @Test
    void 앞의_시도는_뒤에_말이_있을_때만_거르는_조건() {
        assertThat(LocationSearchService.parse("대전 반석역")).isEqualTo(new LocationSearchService.Query("대전광역시", "반석역"));
        assertThat(LocationSearchService.parse("광주 충장로")).isEqualTo(new LocationSearchService.Query("전남광주통합특별시", "충장로"));
        assertThat(LocationSearchService.parse("서울숲")).isEqualTo(new LocationSearchService.Query(null, "서울숲"));
        assertThat(LocationSearchService.parse("강남대로 396")).isEqualTo(new LocationSearchService.Query(null, "강남대로 396"));
    }

    @Test
    void 역은_역_이름을_붙인_가게들로_찾고_법정동도_본다() {
        given(repository.stations(eq("반석역"), eq("반석역"), eq("대전광역시"), anyInt()))
                .willReturn(List.of(new LocationResult("station", "반석역", "대전광역시 유성구", 36.39, 127.31, 700)));
        given(repository.legalDongs(eq("반석동"), eq("대전광역시"), anyInt()))
                .willReturn(List.of(new LocationResult("area", "반석동", "대전광역시 유성구", 36.39, 127.31, 1500)));
        given(cache.increment(anyString(), any())).willReturn(1L);

        List<LocationResult> result = service.search("대전 반석역");

        assertThat(result).extracting(LocationResult::kind).containsExactly("station", "area");
        assertThat(result.get(0).name()).isEqualTo("반석역");
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
        given(repository.areas(eq("성수"), isNull(), anyInt())).willReturn(List.of(new LocationResult("area", "성수1가1동", "서울특별시 성동구", 37.54, 127.05, 1500)));
        given(repository.landmarks(eq("성수동"), isNull(), anyInt())).willReturn(List.of(new LocationResult("place", "성수동 카페거리", "서울", 37.54, 127.05, 1500)));
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
    void 주소_찾기는_Geocoding_결과를_좌표와_함께_돌려준다() {
        given(cache.increment(anyString(), any())).willReturn(1L);
        given(geocoder.geocode("울산 북구 정자1길 111")).willReturn(List.of(
                new NaverGeocodeClient.Address("울산광역시 북구 정자1길 111", "울산광역시 북구 정자동 1", 35.6, 129.4)));

        List<AddressResult> result = service.addresses(" 울산 북구  정자1길 111 ");

        assertThat(result).containsExactly(new AddressResult("울산광역시 북구 정자1길 111", "울산광역시 북구 정자동 1", 35.6, 129.4));
    }

    @Test
    void 주소_찾기는_키가_없거나_한도를_넘으면_503() {
        assertThatThrownBy(() -> service.addresses("가")).isInstanceOf(PlaceException.class);
        given(cache.increment(anyString(), any())).willReturn(3L);
        assertThatThrownBy(() -> service.addresses("정자1길 111")).hasMessageContaining("주소 찾기를 쓸 수 없어요");
        given(geocoder.configured()).willReturn(false);
        assertThatThrownBy(() -> service.addresses("정자1길 111")).hasMessageContaining("주소 찾기를 쓸 수 없어요");
        verify(geocoder, never()).geocode(anyString());
    }

    @Test
    void Redis_가_안_되면_Geocoding_을_부르지_않는다() {
        given(cache.increment(anyString(), any())).willReturn(-1L);
        service.search("강남대로 396");
        verify(geocoder, never()).geocode(anyString());
    }

    @Test
    void 우리_데이터와_주소로_못_찾은_상권은_네이버에서_이름이_든_가게_자리로() {
        given(naver.search("안양일번가")).willReturn(List.of(
                new NaverLocalSearchClient.Item("엉뚱한 가게", "음식점", "경기도 안양시 만안구 안양동 2", "", 37.30, 126.80),
                new NaverLocalSearchClient.Item("KFC 안양일번가점", "패스트푸드", "경기도 안양시 만안구 안양동 1", "경기도 안양시 만안구 안양로 1", 37.40, 126.92)));

        List<LocationResult> found = service.search("안양일번가");

        assertThat(found).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("named");
            assertThat(r.name()).isEqualTo("안양일번가");
            assertThat(r.detail()).isEqualTo("경기도 안양시 만안구 안양로 1");
            assertThat(r.lat()).isEqualTo(37.40);
        });
    }

    @Test
    void 이름이_든_가게가_없거나_네이버를_못_쓰면_빈_목록() {
        given(naver.search("놀거리많은곳")).willReturn(List.of(
                new NaverLocalSearchClient.Item("엉뚱한 가게", "음식점", "경기도 안양시", "", 37.30, 126.80)));
        assertThat(service.searchNamed("놀거리많은곳")).isEmpty();

        given(naver.search("안양일번가")).willThrow(PlaceException.searchUnavailable());
        assertThat(service.searchNamed("안양일번가")).isEmpty();
    }

    @Test
    void 네이버에서_이름이_똑같은_가게는_가게로() {
        given(naver.search("래빗홀버거컴퍼니")).willReturn(List.of(
                new NaverLocalSearchClient.Item("래빗홀버거컴퍼니 건대점", "음식점>햄버거", "서울특별시 광진구 화양동 1", "서울특별시 광진구 능동로 1", 37.54, 127.07)));

        assertThat(service.searchNamed("래빗홀버거컴퍼니")).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("shop");
            assertThat(r.placeId()).isNull();
            assertThat(r.category()).isEqualTo("restaurant");
        });

        // 이름이 똑같아도 상권·거리 같은 항목은 가게가 아니라 지역
        given(naver.search("안양일번가")).willReturn(List.of(
                new NaverLocalSearchClient.Item("안양일번가", "쇼핑,유통>상가,아케이드", "경기도 안양시 만안구 안양동", "", 37.40, 126.92)));
        assertThat(service.searchNamed("안양일번가")).singleElement().satisfies(r -> assertThat(r.kind()).isEqualTo("named"));
    }

    @Test
    void 네이버_분류를_우리_분류로() {
        assertThat(LocationSearchService.categoryOf("카페,디저트>카페")).isEqualTo("cafe");
        assertThat(LocationSearchService.categoryOf("술집>요리주점")).isEqualTo("bar");
        assertThat(LocationSearchService.categoryOf("음식점>햄버거")).isEqualTo("restaurant");
        assertThat(LocationSearchService.categoryOf("쇼핑,유통>문구,팬시용품")).isEqualTo("shop");
    }

    @Test
    void 체인_가게는_지점이나_동네를_붙이면_그_지점() {
        LocationResult onion = new LocationResult("shop", "어니언 성수", "서울특별시 성동구 아차산로9길 8", 37.54, 127.05, 500, 418L, "cafe");
        given(repository.shops("어니언성수", null)).willReturn(List.of(onion));
        assertThat(service.search("어니언 성수점")).contains(onion);   // 이름에 지점명을 붙여 둔 가게

        LocationResult banseok = new LocationResult("shop", "스타벅스 대전반석역", "대전광역시 유성구", 36.39, 127.31, 500, 576332L, "cafe");
        given(repository.shopBranch("스타벅스", "반석", null)).willReturn(List.of(banseok));
        assertThat(service.search("스타벅스 반석")).contains(banseok);   // 지점명·주소에서 찾음
    }
}
