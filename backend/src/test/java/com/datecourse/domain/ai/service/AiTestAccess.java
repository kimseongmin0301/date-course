package com.datecourse.domain.ai.service;

import java.util.List;
import java.util.Map;

import com.datecourse.domain.ai.dto.AiRecommendResponse;
import com.datecourse.domain.ai.repository.AiRepository;
import com.datecourse.domain.keyword.dto.KeywordResponse;

import tools.jackson.databind.JsonNode;

/** 테스트에서 패키지 안의 검수 함수를 부르기 위한 다리 */
public final class AiTestAccess {

    private AiTestAccess() {
    }

    public static AiParseService.Accepted accept(JsonNode ai, String query, Map<String, KeywordResponse> byName) {
        return AiParseService.accept(ai, query, byName);
    }

    public static AiRecommendService.Candidate candidate(long id, String title, List<String> names) {
        return new AiRecommendService.Candidate(id, title, "제주시", 166, names, names, List.of(), true);
    }

    public static List<AiRecommendResponse.Pick> accept(JsonNode answer, List<AiRecommendService.Candidate> candidates) {
        return AiRecommendService.accept(answer, candidates);
    }

    public static String checkReason(String raw, AiRecommendService.Candidate mine, List<AiRecommendService.Candidate> all) {
        return AiRecommendService.checkReason(raw, mine, all);
    }

    public static String text(AiRepository.CourseText c) {
        return CourseEmbeddingService.text(c);
    }

    public static CourseEmbeddingService indexOf(List<AiRepository.Stored> stored) {
        CourseEmbeddingService service = new CourseEmbeddingService(null, null);
        service.setIndex(stored);
        return service;
    }
}
