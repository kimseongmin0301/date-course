package com.datecourse.domain.keyword.service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.datecourse.domain.keyword.dto.KeywordResponse;
import com.datecourse.domain.keyword.entity.Keyword;
import com.datecourse.domain.keyword.repository.KeywordRepository;
import com.datecourse.global.cache.JsonCache;

import tools.jackson.core.type.TypeReference;

@Service
public class KeywordService {

    /** 키워드는 마이그레이션으로만 바뀌므로 길게 둔다 (바꾼 뒤엔 하루 안에 반영되거나, 키를 지운다) */
    static final String CACHE_KEY = "keywords";
    static final Duration CACHE_TTL = Duration.ofHours(24);

    private final KeywordRepository keywords;
    private final JsonCache cache;

    public KeywordService(KeywordRepository keywords, JsonCache cache) {
        this.keywords = keywords;
        this.cache = cache;
    }

    /** 화면 순서의 키워드 전부: 분위기 → 활동 → 실내외 → 예산 (KeywordKind 순서), 그 안에서 sort_order 순 */
    @Transactional(readOnly = true)
    public List<KeywordResponse> all() {
        return cache.getOrLoad(CACHE_KEY, CACHE_TTL, new TypeReference<List<KeywordResponse>>() { },
                () -> keywords.findAll().stream()
                        .sorted(Comparator.comparing((Keyword k) -> k.getKind().ordinal()).thenComparing(Keyword::getSortOrder))
                        .map(KeywordResponse::from)
                        .toList());
    }
}
