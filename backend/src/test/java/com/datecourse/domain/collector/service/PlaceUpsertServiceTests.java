package com.datecourse.domain.collector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.datecourse.domain.collector.dto.PlaceCandidate;
import com.datecourse.domain.collector.util.Geo;
import com.datecourse.domain.place.entity.Place;
import com.datecourse.domain.place.entity.PlaceCategory;
import com.datecourse.domain.place.entity.PlaceMergeCandidate;
import com.datecourse.domain.place.entity.PlaceSource;
import com.datecourse.domain.place.entity.PlaceSourceType;
import com.datecourse.domain.place.repository.PlaceMergeCandidateRepository;
import com.datecourse.domain.place.repository.PlaceRepository;
import com.datecourse.domain.place.repository.PlaceSourceRepository;

class PlaceUpsertServiceTests {

    static final double LAT = 37.5;
    static final double LNG = 127.0;

    PlaceRepository places;
    PlaceSourceRepository sources;
    PlaceMergeCandidateRepository candidates;
    PlaceUpsertService service;

    @BeforeEach
    void setUp() {
        places = mock(PlaceRepository.class);
        sources = mock(PlaceSourceRepository.class);
        candidates = mock(PlaceMergeCandidateRepository.class);
        service = new PlaceUpsertService(places, sources, candidates);
        given(sources.findBySourceAndExternalId(any(), any())).willReturn(Optional.empty());
        given(places.save(any(Place.class))).willAnswer(inv -> withId(inv.getArgument(0), 99L));
    }

    static Place withId(Place p, long id) {
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    static Place existing(long id, String branch, double metersNorth) {
        return withId(new Place("카페 온", branch, "카페온", PlaceCategory.CAFE, null,
                LAT + Geo.latDelta(metersNorth), LNG, "서울특별시", "종로구", null), id);
    }

    static PlaceCandidate candidate(String branch) {
        return new PlaceCandidate(PlaceSourceType.SBIZ, "S1", "카페 온", "카페 온", branch, PlaceCategory.CAFE, "카페",
                LAT, LNG, "서울특별시", "종로구", "삼청동", "서울특별시 종로구 북촌로 1", null, "02-1", null, null);
    }

    void nearby(Place... found) {
        given(places.findByNormalizedNameAndLatBetweenAndLngBetween(eq("카페온"), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of(found));
    }

    @Test
    void 이미_본_원본은_시각만_고친다() {
        Place place = existing(1, null, 0);
        PlaceSource source = new PlaceSource(place, PlaceSourceType.SBIZ, "S1", "옛 이름");
        given(sources.findBySourceAndExternalId(PlaceSourceType.SBIZ, "S1")).willReturn(Optional.of(source));

        assertThat(service.upsert(candidate(null))).isEqualTo(PlaceUpsertService.Result.SEEN_AGAIN);
        assertThat(source.getRawName()).isEqualTo("카페 온");
        verify(places, never()).save(any());
    }

    @Test
    void 이름이_같고_50m_안이면_같은_장소에_출처만_더한다() {
        Place place = existing(1, null, 30);
        nearby(place);

        assertThat(service.upsert(candidate(null))).isEqualTo(PlaceUpsertService.Result.MERGED);
        verify(sources).save(any(PlaceSource.class));
        verify(places, never()).save(any());
        assertThat(place.getRoadAddress()).isEqualTo("서울특별시 종로구 북촌로 1");
    }

    @Test
    void 이름이_같고_50에서_150m_면_새_장소와_중복_후보() {
        nearby(existing(1, null, 100));

        assertThat(service.upsert(candidate(null))).isEqualTo(PlaceUpsertService.Result.CREATED_WITH_CANDIDATE);
        verify(places).save(any(Place.class));
        verify(candidates).save(any(PlaceMergeCandidate.class));
    }

    @Test
    void 지점명이_다르면_가까워도_다른_가게() {
        nearby(existing(1, "강남점", 10));

        assertThat(service.upsert(candidate("역삼점"))).isEqualTo(PlaceUpsertService.Result.CREATED);
        verify(candidates, never()).save(any());
    }

    @Test
    void 같은_이름이_없으면_새_장소() {
        nearby();

        assertThat(service.upsert(candidate(null))).isEqualTo(PlaceUpsertService.Result.CREATED);
        verify(places).save(any(Place.class));
        verify(sources).save(any(PlaceSource.class));
    }
}
