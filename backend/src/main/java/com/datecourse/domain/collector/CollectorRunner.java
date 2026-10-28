package com.datecourse.domain.collector;

import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import com.datecourse.domain.collector.service.PlaceKeywordTagger;
import com.datecourse.domain.collector.service.SbizImporter;
import com.datecourse.domain.collector.service.TourCollector;
import com.datecourse.domain.course.service.AutoCourseService;

/**
 * 장소 수집을 명령으로 돌린다 (웹 서버 없이 실행하고 끝나면 종료).
 *   ./gradlew bootRun --args="--collect=tour"            TourAPI 전국
 *   ./gradlew bootRun --args="--collect=tour --area=1"   TourAPI 서울만 (areaCode)
 *   ./gradlew bootRun --args="--collect=sbiz --path=../data/sbiz/202606"              상가정보 CSV 폴더 전부
 *   ./gradlew bootRun --args="--collect=sbiz --path=../data/sbiz/202606 --only=서울"  파일 이름에 '서울'이 든 것만
 *   ./gradlew bootRun --args="--collect=keywords"        장소 키워드 다시 붙이기 + 자동 코스 다시 만들기 (규칙을 고친 뒤)
 *   ./gradlew bootRun --args="--collect=courses"         자동 코스만 다시 만들기
 * tour·sbiz 수집이 끝나면 키워드와 자동 코스도 다시 만든다.
 */
@Component
public class CollectorRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CollectorRunner.class);

    private final TourCollector tour;
    private final SbizImporter sbiz;
    private final PlaceKeywordTagger tagger;
    private final AutoCourseService courses;
    private final ConfigurableApplicationContext context;

    public CollectorRunner(TourCollector tour, SbizImporter sbiz, PlaceKeywordTagger tagger, AutoCourseService courses,
                           ConfigurableApplicationContext context) {
        this.tour = tour;
        this.sbiz = sbiz;
        this.tagger = tagger;
        this.courses = courses;
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
                case "tour" -> {
                    tour.collect(intOption(args, "area"));
                    afterPlacesChanged();
                }
                case "sbiz" -> {
                    String path = option(args, "path");
                    if (path == null) {
                        throw new IllegalArgumentException("--path=상가정보 CSV 폴더 또는 파일 을 주세요 (예: ../data/sbiz/202606)");
                    }
                    sbiz.importPath(Path.of(path), option(args, "only"));
                    afterPlacesChanged();
                }
                case "keywords" -> afterPlacesChanged();
                case "courses" -> courses.rebuildAll();
                default -> {
                    log.error("알 수 없는 수집 대상: {} (tour, sbiz, keywords, courses)", what.get(0));
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

    /** 장소가 바뀌면 키워드를 다시 붙이고, 그 키워드로 자동 코스를 다시 만든다 */
    private void afterPlacesChanged() {
        tagger.tagAll();
        courses.rebuildAll();
    }

    private static Integer intOption(ApplicationArguments args, String name) {
        String value = option(args, name);
        return value == null ? null : Integer.valueOf(value);
    }

    private static String option(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        return values == null || values.isEmpty() || values.get(0).isBlank() ? null : values.get(0);
    }
}
