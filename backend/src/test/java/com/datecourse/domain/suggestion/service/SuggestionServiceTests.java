package com.datecourse.domain.suggestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.datecourse.domain.suggestion.repository.SuggestionRepository;
import com.datecourse.domain.suggestion.repository.SuggestionRepository.Row;

class SuggestionServiceTests {

    SuggestionRepository repository = mock(SuggestionRepository.class);
    // 실제 목록 대신 지어낸 말로 (막을 말 "막을말", 가리기만 할 말 "애매")
    ProfanityFilter profanity = new ProfanityFilter(List.of("막을말"), List.of(), List.of("애매"));
    SuggestionService service = new SuggestionService(repository, profanity);

    static final double LAT = 35.6215;
    static final double LNG = 129.4464;
    List<Row> rows = new ArrayList<>();

    static Row row(long id, UUID visitor, String status, double northMeters) {
        return new Row(id, visitor, "카페 높", "카페높", "울산광역시 북구 정자1길 111", LAT + northMeters / 111_320, LNG,
                "울산광역시", "북구", status, null, OffsetDateTime.now());
    }

    @BeforeEach
    void setUp() {
        given(repository.byName("카페높")).willAnswer(inv -> List.copyOf(rows));
        given(repository.existingPlace(anyString(), anyDouble(), anyDouble(), anyDouble())).willReturn(Optional.empty());
        given(repository.insert(any(), anyString(), anyString(), anyString(), anyDouble(), anyDouble(), any(), any(), anyString(), any(), any()))
                .willAnswer(inv -> {
                    long id = 100 + rows.size();
                    rows.add(new Row(id, inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3),
                            inv.getArgument(4), inv.getArgument(5), null, null, inv.getArgument(8), null, OffsetDateTime.now()));
                    return id;
                });
        given(repository.find(anyLong())).willAnswer(inv -> rows.stream().filter(r -> r.id() == (long) inv.getArgument(0)).findFirst());
    }

    Optional<String> record(UUID visitor) {
        return service.record(visitor, "카페 높", "울산광역시 북구 정자1길 111", LAT, LNG);
    }

    @Test
    void 서로_다른_세_사람이_같은_가게를_넣으면_공개() {
        rows.add(row(1, UUID.randomUUID(), "pending", 0));
        rows.add(row(2, UUID.randomUUID(), "pending", 20));     // 20m 옆: 같은 가게
        rows.add(row(3, UUID.randomUUID(), "pending", 300));    // 300m 떨어진 같은 이름: 다른 가게

        assertThat(record(UUID.randomUUID())).contains("public");

        verify(repository).setStatus(1, "public", null);        // 가장 먼저 알려 준 것을 공개
        verify(repository).setStatus(2, "merged", 1L);
        verify(repository, never()).setStatus(eq(3L), anyString(), any());
    }

    @Test
    void 두_사람까지는_모으기만_한다() {
        rows.add(row(1, UUID.randomUUID(), "pending", 0));
        assertThat(record(UUID.randomUUID())).contains("pending");
        verify(repository, never()).setStatus(anyLong(), eq("public"), any());
    }

    @Test
    void 같은_사람이_다시_넣어도_한_번만_센다() {
        UUID me = UUID.randomUUID();
        rows.add(row(1, me, "pending", 0));
        assertThat(record(me)).isEmpty();
    }

    @Test
    void 이미_우리_데이터에_있거나_공개된_가게면_합친다() {
        given(repository.existingPlace(eq("카페높"), anyDouble(), anyDouble(), anyDouble())).willReturn(Optional.of(77L));
        assertThat(record(UUID.randomUUID())).contains("existing");

        given(repository.existingPlace(anyString(), anyDouble(), anyDouble(), anyDouble())).willReturn(Optional.empty());
        rows.add(row(5, UUID.randomUUID(), "public", 10));
        assertThat(record(UUID.randomUUID())).contains("merged");
    }

    @Test
    void 막을_말은_모으지_않고_애매한_말은_관리자만_공개() {
        assertThat(service.record(UUID.randomUUID(), "막을말 카페", "주소", LAT, LNG)).isEmpty();
        verify(repository, never()).insert(any(), anyString(), anyString(), anyString(), anyDouble(), anyDouble(), any(), any(), anyString(), any(), any());

        assertThat(service.record(UUID.randomUUID(), "애매 카페", "주소", LAT, LNG)).contains("review");
    }

    @Test
    void 하루에_넣을_수_있는_수를_넘으면_모으지_않는다() {
        given(repository.countToday(any())).willReturn(SuggestionService.DAILY_MAX);
        assertThat(record(UUID.randomUUID())).isEmpty();
    }

    @Test
    void 관리자가_승인하면_공개하고_같은_가게의_기다리는_제안은_합친다() {
        rows.add(row(1, UUID.randomUUID(), "review", 0));
        rows.add(row(2, UUID.randomUUID(), "pending", 10));
        service.approve(1);
        verify(repository).setStatus(1, "public", null);
        verify(repository).setStatus(2, "merged", 1L);
        verify(repository, never()).setStatus(eq(1L), eq("merged"), isNull());
    }
}
