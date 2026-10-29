package com.datecourse.domain.suggestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 실제 목록(resources/moderation)으로 확인한다. 걸리면 30분 제한이라 오탐이 없어야 한다 */
class ProfanityFilterTests {

    static final ProfanityFilter filter = new ProfanityFilter();

    @ParameterizedTest
    @ValueSource(strings = {
            "시발점부터 다시 생각해 보자", "오늘 노래 미친 듯이 좋다", "보지 마세요 스포 있어요", "남자지만 이 노래 좋아함",
            "새끼손가락 다쳤어", "강아지 새끼가 귀엽다", "사정이 있어서 먼저 나갈게요", "발기인 대회 뉴스 봤어",
            "자위대 관련 기사", "얼싸안고 울었다", "땅따먹기 하던 시절", "십팔번 노래 틀어줘", "시바견 너무 귀여워",
            "수박씨 발라 먹기", "허리띠를 졸라매자", "존경합니다", "씨앗을 심었어요", "씨 발라내고 먹어", "개새로운 기분",
            "했으니 미련 없어", "금수저야 동수저야", "전염병 조심하세요", "김씨 바둑 두러 감", "박씨 발견", "출시 발표 났다",
            "10센치 노래 좋아요", "18세 이상", "Shiitake mushroom", "졸린 고양이", "따뜻한 장작", "불멍 최고 ㅋㅋ"
    })
    void cleanTextPasses(String text) {
        assertThat(filter.find(text)).as(text).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "씨발", "아 시발 뭐야", "시1발", "씨 발 놈아", "씨.발", "ㅅㅂ 진짜", "ㅅ ㅂ", "ㅂ ㅅ 같네", "병신", "병1신",
            "존나 좋네", "좆같다", "개.새.끼", "개 새 끼", "tlqkf", "니애미", "지랄하네", "섹스", "f u c k you", "FUCK",
            "ｆｕｃｋ", "보지털", "야 이 미친놈아", "씨이발"
    })
    void profanityIsFound(String text) {
        assertThat(filter.find(text)).as(text).isPresent();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', value = {
            "보지 마세요 스포 있어요|** 마세요 스포 있어요",
            "남자지만 이 노래 좋아함|남**만 이 노래 좋아함",
            "새끼손가락 다쳤어|**손가락 다쳤어",
            "오늘 노래 미친 듯이 좋다|오늘 노래 ** 듯이 좋다",
            "보.지|*.*",
            "Sexy music|***y music",
            "졸린 고양이 안녕하세요|졸린 고양이 안녕하세요",
            "보 지|보 지"
    })
    void ambiguousWordsAreMaskedNotBanned(String text, String masked) {
        assertThat(filter.mask(text)).isEqualTo(masked);
        assertThat(filter.find(text)).as("가리기만 하고 제한하지 않음: " + text).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"딜도", "애무", "오나홀", "빠구리", "hentai", "정액"})
    void moreSexualWordsAreFound(String text) {
        assertThat(filter.find(text)).as(text).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"애널리스트 보고서", "정액권 끊었어", "정액제 요금"})
    void sexualWordsInsideNormalWordsPass(String text) {
        assertThat(filter.find(text)).as(text).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void emptyIsFine(String text) {
        assertThat(filter.find(text)).isEmpty();
        assertThat(filter.find(null)).isEmpty();
    }
}
