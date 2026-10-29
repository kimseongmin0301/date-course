package com.datecourse.domain.course.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 코스 순위 점수(course.score)를 다시 계산한다. 키워드 일치·가까움은 검색할 때 더하고, 여기서는 고정된 부분만:
 *   큐레이션        관리자가 등록한 코스 +20
 *   사용자 반응     10 × ln(1 + 점수). 다녀왔어요 5, 저장 3, 좋아요 1, 90일 지난 반응은 0.3배
 *   동선            10 × (1 − 단계 사이 평균 걷는 시간 ÷ 15분), 0 아래로는 안 감
 *   출처 일치       10 × (상가정보와 TourAPI 에 모두 있는 단계 비율)
 *   지정 업체       10 × (모범음식점 등 공공기관이 지정한 단계 비율)
 *   단계 수         4단계면 +2
 * 반응은 계속 쌓이므로 매일 새벽에 한 번, 자동 코스를 다시 만든 뒤에 한 번 돌린다.
 */
@Service
public class CourseScoreService {

    private static final Logger log = LoggerFactory.getLogger(CourseScoreService.class);

    private final JdbcTemplate jdbc;

    public CourseScoreService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public int recalculate() {
        long started = System.currentTimeMillis();
        int updated = jdbc.update("""
                UPDATE course c
                SET score = x.score, score_updated_at = now()
                FROM (
                    SELECT c2.id,
                           round(((CASE WHEN c2.kind = 'curated' THEN 20 ELSE 0 END)
                                + 10 * ln(1 + COALESCE(r.points, 0))
                                + 10 * greatest(0, 1 - COALESCE(l.avg_walk, 15) / 15.0)
                                + 10 * COALESCE(l.both_share, 0)
                                + 10 * COALESCE(l.badge_share, 0)
                                + (CASE WHEN l.steps >= 4 THEN 2 ELSE 0 END))::numeric, 3)::double precision AS score
                    FROM course c2
                    LEFT JOIN (
                        SELECT cs.course_id, avg(cs.walk_minutes) AS avg_walk, count(*) AS steps,
                               avg(CASE WHEN (SELECT count(DISTINCT s.source) FROM place_source s WHERE s.place_id = cs.place_id) = 2
                                        THEN 1.0 ELSE 0 END) AS both_share,
                               avg(CASE WHEN EXISTS (SELECT 1 FROM place_badge b WHERE b.place_id = cs.place_id)
                                        THEN 1.0 ELSE 0 END) AS badge_share
                        FROM course_step cs GROUP BY cs.course_id
                    ) l ON l.course_id = c2.id
                    LEFT JOIN (
                        SELECT course_id,
                               sum((CASE kind WHEN 'visited' THEN 5 WHEN 'save' THEN 3 ELSE 1 END)
                                   * (CASE WHEN created_at > now() - interval '90 days' THEN 1.0 ELSE 0.3 END)) AS points
                        FROM reaction GROUP BY course_id
                    ) r ON r.course_id = c2.id
                ) x
                WHERE x.id = c.id""");
        log.info("코스 순위 점수: {}개 ({}초)", updated, (System.currentTimeMillis() - started) / 1000);
        return updated;
    }
}
