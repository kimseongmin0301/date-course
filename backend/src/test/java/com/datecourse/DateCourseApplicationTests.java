package com.datecourse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.datecourse.domain.keyword.entity.Keyword;
import com.datecourse.domain.keyword.entity.KeywordKind;
import com.datecourse.domain.keyword.repository.KeywordRepository;

/**
 * 실제 DB 에 접속해 앱이 뜨는지 확인한다 (엔티티와 date_course 스키마의 테이블이 맞는지 validate, 읽기만 함).
 * .env 가 필요해서 CI(GitHub Actions)에서는 빼고 돌린다: ./gradlew test -Pci
 */
@Tag("integration")
@SpringBootTest
class DateCourseApplicationTests {

    @Autowired
    KeywordRepository keywords;

    @Test
    void contextLoads() {
    }

    @Test
    void 키워드_17개를_분류별로_읽는다() {
        List<Keyword> all = keywords.findAllByOrderByKindAscSortOrderAsc();

        assertThat(all).hasSize(17);
        assertThat(all).filteredOn(k -> k.getKind() == KeywordKind.BUDGET).hasSize(3);
        assertThat(keywords.findByCode("quiet")).hasValueSatisfying(k -> assertThat(k.getName()).isEqualTo("조용"));
    }
}
