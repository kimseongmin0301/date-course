package com.datecourse.domain.keyword.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.datecourse.domain.keyword.dto.KeywordResponse;
import com.datecourse.domain.keyword.entity.Keyword;
import com.datecourse.domain.keyword.entity.KeywordKind;
import com.datecourse.domain.keyword.repository.KeywordRepository;
import com.datecourse.global.cache.JsonCache;

class KeywordServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void 분위기_활동_실내외_예산_순서로() throws Exception {
        KeywordRepository repository = mock(KeywordRepository.class);
        JsonCache cache = mock(JsonCache.class);
        given(cache.getOrLoad(any(), any(java.time.Duration.class), any(), any())).willAnswer(inv -> ((Supplier<List<KeywordResponse>>) inv.getArgument(3)).get());
        given(repository.findAll()).willReturn(List.of(
                make("budget-low", KeywordKind.BUDGET, 10),
                make("walk", KeywordKind.ACTIVITY, 10),
                make("indoor", KeywordKind.SETTING, 10),
                make("romantic", KeywordKind.MOOD, 20),
                make("quiet", KeywordKind.MOOD, 10)));

        List<KeywordResponse> result = new KeywordService(repository, cache).all();

        assertThat(result).extracting(KeywordResponse::code).containsExactly("quiet", "romantic", "walk", "indoor", "budget-low");
    }

    static Keyword make(String code, KeywordKind kind, int order) throws Exception {
        var constructor = Keyword.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Keyword k = constructor.newInstance();
        ReflectionTestUtils.setField(k, "code", code);
        ReflectionTestUtils.setField(k, "name", code);
        ReflectionTestUtils.setField(k, "kind", kind);
        ReflectionTestUtils.setField(k, "sortOrder", order);
        return k;
    }
}
