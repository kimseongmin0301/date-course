package com.datecourse.domain.collector;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import com.datecourse.domain.collector.service.TourCollector;

/**
 * 장소 수집을 명령으로 돌린다 (웹 서버 없이 실행하고 끝나면 종료).
 *   ./gradlew bootRun --args="--collect=tour"            TourAPI 전국
 *   ./gradlew bootRun --args="--collect=tour --area=1"   TourAPI 서울만 (areaCode)
 */
@Component
public class CollectorRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CollectorRunner.class);

    private final TourCollector tour;
    private final ConfigurableApplicationContext context;

    public CollectorRunner(TourCollector tour, ConfigurableApplicationContext context) {
        this.tour = tour;
        this.context = context;
    }

    /** main 에서 웹 서버를 끌지 정할 때 쓴다 */
    public static boolean requested(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--collect")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> what = args.getOptionValues("collect");
        if (what == null || what.isEmpty()) {
            return;
        }
        int exitCode = 0;
        try {
            switch (what.get(0)) {
                case "tour" -> tour.collect(intOption(args, "area"));
                default -> {
                    log.error("알 수 없는 수집 대상: {} (tour)", what.get(0));
                    exitCode = 2;
                }
            }
        } catch (RuntimeException e) {
            log.error("수집 실패", e);
            exitCode = 1;
        }
        int code = exitCode;
        System.exit(SpringApplication.exit(context, () -> code));
    }

    private static Integer intOption(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        return values == null || values.isEmpty() ? null : Integer.valueOf(values.get(0));
    }
}
