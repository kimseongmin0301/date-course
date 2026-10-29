package com.datecourse.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.datecourse.domain.course.dto.PlanResponse;
import com.datecourse.domain.course.dto.PlanSaveRequest;
import com.datecourse.domain.course.dto.PlanStepRequest;
import com.datecourse.domain.course.exception.CourseException;
import com.datecourse.domain.course.repository.CourseQueryRepository;
import com.datecourse.domain.course.repository.PlanRepository;

import tools.jackson.databind.json.JsonMapper;

class PlanServiceTests {

    PlanRepository plans = mock(PlanRepository.class);
    CourseQueryRepository places = mock(CourseQueryRepository.class);
    PlanService service = new PlanService(plans, places, JsonMapper.builder().build());
    UUID visitor = UUID.randomUUID();

    static CourseQueryRepository.Candidate place(long id, String name, double northMeters) {
        return new CourseQueryRepository.Candidate(id, name, null, name, "cafe", "카페", 37.5 + northMeters / 111_320, 127.0,
                "서울특별시", "종로구", null, null, null, null, null, Set.of(), false, true, false, false);
    }

    @Test
    void 도착_시간을_고정하면_그때까지_기다리고_늦으면_늦은_시간에() {
        List<PlanService.Stored> steps = List.of(
                new PlanService.Stored(1, "spot", 60, null, "course", 1),
                new PlanService.Stored(2, "meal", 70, 19 * 60, "course", 2),     // 19:00 예약
                new PlanService.Stored(3, "cafe", 30, 20 * 60, "added", null));  // 20:00 이라 했지만 식사가 20:10 에 끝남
        Map<Long, CourseQueryRepository.Candidate> byId = Map.of(1L, place(1, "a", 0), 2L, place(2, "b", 0), 3L, place(3, "c", 0));

        List<int[]> t = PlanService.timeline(steps, byId, 14 * 60);

        assertThat(t.get(0)).containsExactly(840, 900);     // 14:00–15:00
        assertThat(t.get(1)).containsExactly(1140, 1210);   // 19:00 까지 기다림 → 19:00–20:10
        assertThat(t.get(2)).containsExactly(1211, 1241);   // 걷는 1분 뒤 20:11 (고정 20:00 보다 늦음)
    }

    @Test
    void 시간_글자_바꾸기() {
        assertThat(PlanService.minuteOf("09:05")).isEqualTo(545);
        assertThat(PlanService.clock(1500)).isEqualTo("다음날 01:00");
        assertThatThrownBy(() -> PlanService.minuteOf("25:00")).isInstanceOf(CourseException.class);
        assertThatThrownBy(() -> PlanService.dateOf("2026/11/01")).isInstanceOf(CourseException.class);
    }

    @Test
    void 저장하면_장소_id_만_두고_읽을_때_장소를_이어_붙인다() {
        given(plans.courseExists(7)).willReturn(true);
        given(places.placesByIds(anyList())).willReturn(List.of(place(11, "미술관", 0), place(12, "카페", 200)));
        String stored = "[{\"placeId\":11,\"role\":\"spot\",\"stay\":45,\"fixed\":null,\"origin\":\"course\",\"courseOrder\":1},"
                + "{\"placeId\":12,\"role\":\"mine\",\"stay\":25,\"fixed\":null,\"origin\":\"added\",\"courseOrder\":null}]";
        given(plans.find(visitor, 7)).willReturn(Optional.empty(), Optional.of(new PlanRepository.Row(7, "코스", "서울특별시", "종로구",
                LocalDate.parse("2026-11-01"), 870, stored, OffsetDateTime.now())));

        PlanResponse saved = service.save(visitor, 7, new PlanSaveRequest("2026-11-01", "14:30", List.of(
                new PlanStepRequest(11L, "spot", 45, null, "course", 1),
                new PlanStepRequest(12L, "mine", 25, null, "added", 3))));

        verify(plans).upsert(eq(visitor), eq(7L), any(), eq(870), anyString());
        assertThat(saved.start()).isEqualTo("14:30");
        assertThat(saved.steps()).extracting(s -> s.place().name()).containsExactly("미술관", "카페");
        assertThat(saved.steps().get(1).courseOrder()).isNull();
    }

    @Test
    void 없는_장소나_이상한_값은_저장하지_않는다() {
        given(plans.courseExists(7)).willReturn(true);
        given(places.placesByIds(anyList())).willReturn(List.of(place(11, "미술관", 0)));
        assertThatThrownBy(() -> service.save(visitor, 7, new PlanSaveRequest(null, "14:00",
                List.of(new PlanStepRequest(99L, "spot", 60, null, "course", 1))))).isInstanceOf(CourseException.class);
        assertThatThrownBy(() -> service.save(visitor, 7, new PlanSaveRequest(null, "14:00",
                List.of(new PlanStepRequest(11L, "spot", 0, null, "course", 1))))).isInstanceOf(CourseException.class);
        assertThatThrownBy(() -> service.save(visitor, 7, new PlanSaveRequest(null, "14:00", List.of())))
                .isInstanceOf(CourseException.class);
        verify(plans, never()).upsert(any(), anyLong(), any(), anyInt(), anyString());
    }

    @Test
    void 일정은_50개까지() {
        given(plans.courseExists(8)).willReturn(true);
        given(places.placesByIds(anyList())).willReturn(List.of(place(11, "미술관", 0)));
        given(plans.find(visitor, 8)).willReturn(Optional.empty());
        given(plans.count(visitor)).willReturn(PlanService.MAX_PLANS);
        assertThatThrownBy(() -> service.save(visitor, 8, new PlanSaveRequest(null, "14:00",
                List.of(new PlanStepRequest(11L, "spot", 60, null, "course", 1))))).hasMessageContaining("50개까지");
    }
}
