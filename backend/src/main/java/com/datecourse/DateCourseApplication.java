package com.datecourse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.datecourse.domain.collector.CollectorRunner;

@SpringBootApplication
public class DateCourseApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DateCourseApplication.class);
        // 장소 수집(--collect=…)은 웹 서버 없이 돌리고 끝나면 종료한다 (돌고 있는 서버와 포트가 겹치지 않게)
        if (CollectorRunner.requested(args)) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.run(args);
    }
}
