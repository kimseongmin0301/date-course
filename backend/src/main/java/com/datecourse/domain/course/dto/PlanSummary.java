package com.datecourse.domain.course.dto;

import java.util.List;

/** 내 저장 목록의 일정 하나. end: 끝나는 시간 HH:MM (자정을 넘기면 "다음날 HH:MM") */
public record PlanSummary(long courseId, String title, String sido, String sigungu, String date, String start, String end,
                          List<String> stepNames, String savedAt) {
}
