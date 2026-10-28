package com.datecourse.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @Scheduled 작업 켜기 (코스 순위 점수 매일 다시 계산 등) */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
