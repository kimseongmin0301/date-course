package com.datecourse.domain.collector.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.datecourse.domain.place.entity.PlaceCategory;

class PlaceKeywordRulesTests {

    @Test
    void 규칙의_code_는_모두_키워드_시드에_있다() throws Exception {
        String seed = Files.readString(Path.of("../db/V2__seed_keyword.sql"));
        Set<String> seeded = new HashSet<>();
        Matcher m = Pattern.compile("\\('([a-z-]+)',").matcher(seed);
        while (m.find()) {
            seeded.add(m.group(1));
        }
        assertThat(seeded).hasSize(17);
        assertThat(seeded).containsAll(PlaceKeywordRules.allCodes());
    }

    @Test
    void 상가정보_업종() {
        assertThat(PlaceKeywordRules.codesFor(PlaceCategory.ACTIVITY, "노래방", "코인노래방"))
                .containsExactlyInAnyOrder("play", "lively", "indoor");
        assertThat(PlaceKeywordRules.codesFor(PlaceCategory.RESTAURANT, "파스타/스테이크", "어느식당"))
                .containsExactlyInAnyOrder("food-tour", "romantic", "indoor", "budget-high");
        assertThat(PlaceKeywordRules.codesFor(PlaceCategory.RESTAURANT, "김밥/만두/분식", "분식집"))
                .containsExactlyInAnyOrder("food-tour", "indoor", "budget-low");
    }

    @Test
    void 모르는_음식점은_중간_예산_카페는_낮은_예산() {
        assertThat(PlaceKeywordRules.codesFor(PlaceCategory.RESTAURANT, "백반/한정식", "밥집"))
                .containsExactlyInAnyOrder("food-tour", "indoor", "budget-mid");
        assertThat(PlaceKeywordRules.codesFor(PlaceCategory.CAFE, "카페", "커피"))
                .containsExactlyInAnyOrder("indoor", "budget-low");
    }

    @Test
    void TourAPI_코드는_자세한_규칙이_실내외를_덮어쓴다() {
        // NA(야외) 보다 NA030100 동굴(실내)이 이긴다
        assertThat(PlaceKeywordRules.codesFor(PlaceCategory.ATTRACTION, "NA030100", "어느동굴"))
                .contains("indoor", "walk", "unique").doesNotContain("outdoor");
        assertThat(PlaceKeywordRules.codesFor(PlaceCategory.ATTRACTION, "NA020900", "해수욕장"))
                .containsExactlyInAnyOrder("outdoor", "walk", "romantic");
        assertThat(PlaceKeywordRules.codesFor(PlaceCategory.CULTURE, "VE070600", "시립미술관"))
                .containsExactlyInAnyOrder("indoor", "exhibition", "quiet", "cozy");
    }

    @Test
    void 이름에_든_말() {
        assertThat(PlaceKeywordRules.codesFor(PlaceCategory.BAR, "요리 주점", "루프탑라운지"))
                .containsExactlyInAnyOrder("lively", "night-view", "romantic", "outdoor", "budget-mid");
        assertThat(PlaceKeywordRules.codesFor(PlaceCategory.SHOP, "액세서리/잡화 소매업", "은반지공방"))
                .contains("workshop", "cozy", "shopping", "indoor");
    }

    @Test
    void 규칙이_없으면_실내외만_관광명소는_야외() {
        assertThat(PlaceKeywordRules.codesFor(PlaceCategory.ATTRACTION, "VE110200", "어느역")).containsExactly("outdoor");
        assertThat(PlaceKeywordRules.codesFor(PlaceCategory.CULTURE, null, "어느곳")).containsExactly("indoor");
    }

    @Test
    void 실내외와_예산은_하나씩만() {
        for (String detail : new String[] {"요리 주점", "FD040100", "LS010900", "AC050400", "빵/도넛"}) {
            Set<String> codes = PlaceKeywordRules.codesFor(PlaceCategory.BAR, detail, "루프탑와인바");
            assertThat(codes.stream().filter(c -> c.equals("indoor") || c.equals("outdoor"))).hasSize(1);
            assertThat(codes.stream().filter(c -> c.startsWith("budget-"))).hasSize(1);
        }
    }
}
