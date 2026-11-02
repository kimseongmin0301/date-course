package com.datecourse.domain.ai.dto;

import java.util.List;

/**
 * AI 추천 결과. ai: AI 가 골랐으면 true, 바쁘거나 실패해 순위 위에서 골랐으면 false (message 로 알림).
 * candidates: AI 에게 보여준 후보 수 (그중에서만 고름)
 */
public record AiRecommendResponse(boolean ai, String message, int candidates, List<Pick> picks) {

    /** 고른 코스 하나. reason: AI 가 쓴 한 줄 이유(검수에서 빠지면 null), inRanking: 지금 순위 목록 첫 쪽에 있는 코스인지 */
    public record Pick(long courseId, String title, String sigungu, Integer totalMinutes, List<String> stepNames,
                       String reason, boolean inRanking) {
    }
}
