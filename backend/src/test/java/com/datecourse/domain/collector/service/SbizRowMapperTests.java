package com.datecourse.domain.collector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.datecourse.domain.collector.dto.PlaceCandidate;
import com.datecourse.domain.place.entity.PlaceCategory;
import com.datecourse.domain.place.entity.PlaceSourceType;

class SbizRowMapperTests {

    /** 실제 파일과 같은 39개 열 중 쓰는 열만 앞에 두고 순서를 섞어도 이름으로 찾는다 */
    static final String[] HEADER = {"﻿상가업소번호", "상호명", "지점명", "상권업종대분류명", "상권업종중분류명", "상권업종소분류명",
            "시도명", "시군구명", "행정동명", "지번주소", "도로명주소", "경도", "위도"};

    final SbizRowMapper mapper = new SbizRowMapper(HEADER);

    String[] row(String name, String branch, String middle, String small) {
        return new String[] {"MA01", name, branch, "음식", middle, small, "서울특별시", "성동구", "성수1가2동",
                "서울특별시 성동구 성수동1가 656-297", "서울특별시 성동구 왕십리로 114", "127.0447", "37.5476"};
    }

    @Test
    void 카페_줄을_장소_후보로() {
        PlaceCandidate c = mapper.toCandidate(row("카페 온", "서울숲점", "비알코올 ", "카페")).orElseThrow();

        assertThat(c.source()).isEqualTo(PlaceSourceType.SBIZ);
        assertThat(c.externalId()).isEqualTo("MA01");
        assertThat(c.category()).isEqualTo(PlaceCategory.CAFE);
        assertThat(c.name()).isEqualTo("카페 온");
        assertThat(c.branch()).isEqualTo("서울숲점");
        assertThat(c.rawName()).isEqualTo("카페 온 서울숲점");
        assertThat(c.sigungu()).isEqualTo("성동구");
        assertThat(c.dong()).isEqualTo("성수1가2동");
        assertThat(c.roadAddress()).isEqualTo("서울특별시 성동구 왕십리로 114");
        assertThat(c.lotAddress()).isEqualTo("서울특별시 성동구 성수동1가 656-297");
        assertThat(c.lat()).isEqualTo(37.5476);
    }

    @Test
    void 업종을_우리_분류로() {
        assertThat(SbizRowMapper.category("한식", "백반/한정식")).isEqualTo(PlaceCategory.RESTAURANT);
        assertThat(SbizRowMapper.category("기타 간이", "빵/도넛")).isEqualTo(PlaceCategory.CAFE);
        assertThat(SbizRowMapper.category("주점", "요리 주점")).isEqualTo(PlaceCategory.BAR);
        assertThat(SbizRowMapper.category("장식품 소매", "기념품점")).isEqualTo(PlaceCategory.SHOP);
        assertThat(SbizRowMapper.category("유원지·오락", "노래방")).isEqualTo(PlaceCategory.ACTIVITY);
    }

    @Test
    void 데이트와_먼_업종은_뺀다() {
        assertThat(SbizRowMapper.category("주점", "일반 유흥 주점")).isNull();
        assertThat(SbizRowMapper.category("종합 소매", "편의점")).isNull();
        assertThat(SbizRowMapper.category("구내식당·뷔페", "구내식당")).isNull();
        assertThat(mapper.toCandidate(row("GS25", null, "종합 소매", "편의점"))).isEmpty();
    }

    @Test
    void 지점명_칸이_비면_이름에서_뗀다() {
        PlaceCandidate c = mapper.toCandidate(row("스타벅스 성수점", "", "비알코올 ", "카페")).orElseThrow();
        assertThat(c.name()).isEqualTo("스타벅스");
        assertThat(c.branch()).isEqualTo("성수점");
    }

    @Test
    void 열이_빠진_파일은_거절한다() {
        assertThatThrownBy(() -> new SbizRowMapper(new String[] {"상호명"}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
