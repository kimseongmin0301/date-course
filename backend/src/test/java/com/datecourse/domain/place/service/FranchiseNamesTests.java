package com.datecourse.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FranchiseNamesTests {

    static final FranchiseNames.Names NAMES = new FranchiseNames.Names(
            Set.of("투썸플레이스", "스타벅스", "할리스", "김밥천국", "우리식당", "아지트"),
            Set.of("투썸플레이스", "스타벅스", "할리스"),
            Instant.now());

    @Test
    void 흔한_이름은_체인() {
        assertThat(FranchiseNames.isFranchise("김밥천국", NAMES)).isTrue();
        assertThat(FranchiseNames.isFranchise("투썸플레이스", NAMES)).isTrue();
    }

    @Test
    void 브랜드_뒤에_지역_지점명이_붙어도_체인() {
        assertThat(FranchiseNames.isFranchise("투썸플레이스서초", NAMES)).isTrue();
        assertThat(FranchiseNames.isFranchise("할리스대전건양대", NAMES)).isTrue();
        assertThat(FranchiseNames.isFranchise("스타벅스강남r점", NAMES)).isTrue();
    }

    @Test
    void 지점이_없는_흔한_이름으로_시작하는_동네_가게는_체인이_아니다() {
        // "우리식당"·"아지트"는 흔하지만 지점명이 거의 없어 브랜드가 아님
        assertThat(FranchiseNames.isFranchise("우리식당본점", NAMES)).isFalse();
        assertThat(FranchiseNames.isFranchise("아지트커피", NAMES)).isFalse();
    }

    @Test
    void 브랜드_뒤가_너무_길면_다른_가게() {
        assertThat(FranchiseNames.isFranchise("할리스데이비드슨오토바이전시관", NAMES)).isFalse();
        assertThat(FranchiseNames.isFranchise("조용한동네카페", NAMES)).isFalse();
        assertThat(FranchiseNames.isFranchise("", NAMES)).isFalse();
    }
}
