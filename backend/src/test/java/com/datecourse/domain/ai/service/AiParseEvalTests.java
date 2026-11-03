package com.datecourse.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.datecourse.domain.ai.dto.AiParseResponse;
import com.datecourse.domain.place.client.NaverLocalSearchClient;

/**
 * AI 검색 시험 모음 (resources/ai-eval/queries.tsv): 사용 중에 틀렸던 문장과 대표 문장을 실제 장소 데이터로 알아듣게 하고
 * 칸마다(지역·시도·풍경·메뉴·키워드·실내외·예산) 기대와 비교한다. 규칙을 고친 뒤 예전에 고친 것이 다시 깨지지 않았는지 본다.
 *   - AI(Ollama)는 끄고 규칙 + 우리 데이터만 본다 (같은 문장이면 늘 같은 결과라 점수를 비교할 수 있게)
 *   - 캐시는 돌릴 때마다 새 키 접두어를 써서 운영 캐시를 건드리지 않고, 지난번 결과가 남아 고친 것이 안 보이는 일도 없게 한다
 *   - 결과는 build/reports/ai-eval.txt 에도 남긴다 (통과 수, 칸별 정확도, 틀린 문장)
 * 실제 DB 가 필요해 CI 에서는 빠진다: ./gradlew test --tests "*AiParseEvalTests"
 */
@Tag("integration")
@SpringBootTest(properties = "date-course.ai.enabled=false")
class AiParseEvalTests {

    /** 이보다 적게 통과하면 실패 (규칙을 고쳐 더 맞히면 올린다) */
    static final double MIN_PASS_RATE = 0.9;
    static final List<String> FIELDS = List.of("지역", "시도", "풍경", "메뉴", "키워드", "실내외", "예산");

    @DynamicPropertySource
    static void freshCache(DynamicPropertyRegistry registry) {
        registry.add("date-course.redis.key-prefix", () -> "healing-room:date-course-eval:" + System.currentTimeMillis() + ":");
    }

    @Autowired
    AiParseService parser;

    @Autowired
    NaverLocalSearchClient naver;

    record Case(String query, boolean needsNaver, String[] expect) {
    }

    @Test
    void 시험_문장을_알아듣는다() throws IOException {
        List<Case> cases = load();
        Map<String, int[]> perField = new LinkedHashMap<>();
        FIELDS.forEach(f -> perField.put(f, new int[2]));
        List<String> failures = new ArrayList<>();
        int passed = 0;
        int run = 0;
        for (Case c : cases) {
            if (c.needsNaver() && !naver.configured()) {
                continue;   // 네이버 검색 키가 없는 로컬
            }
            run++;
            AiParseResponse r = parser.parse(c.query(), "127.0.0.1");
            List<String> wrong = new ArrayList<>();
            for (int i = 0; i < FIELDS.size(); i++) {
                String expected = c.expect()[i];
                if ("*".equals(expected)) {
                    continue;
                }
                boolean ok = check(FIELDS.get(i), expected, r);
                int[] count = perField.get(FIELDS.get(i));
                count[1]++;
                if (ok) {
                    count[0]++;
                } else {
                    wrong.add(FIELDS.get(i) + " 기대 " + expected + " / 실제 " + actual(FIELDS.get(i), r));
                }
            }
            if (wrong.isEmpty()) {
                passed++;
            } else {
                failures.add("✗ " + c.query() + " → " + r.understood() + "\n    " + String.join("\n    ", wrong));
            }
        }

        StringBuilder report = new StringBuilder("AI 검색 시험: 통과 " + passed + " / " + run + " (규칙 + 우리 데이터, AI 끔)\n");
        perField.forEach((f, n) -> report.append("  ").append(f).append(' ').append(n[0]).append('/').append(n[1]).append('\n'));
        failures.forEach(f -> report.append(f).append('\n'));
        System.out.println(report);
        Path out = Path.of("build", "reports", "ai-eval.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, StandardCharsets.UTF_8);

        assertThat((double) passed / run).as(report.toString()).isGreaterThanOrEqualTo(MIN_PASS_RATE);
    }

    static boolean check(String field, String expected, AiParseResponse r) {
        boolean none = "-".equals(expected);
        return switch (field) {
            case "지역" -> none ? r.area() == null
                    : r.area() != null && r.area().name().replace(" ", "").contains(expected.replace(" ", ""));
            case "시도" -> none ? r.sidos().isEmpty() : new HashSet<>(r.sidos()).equals(Set.of(expected.split(",")));
            case "풍경" -> none ? r.scene() == null : expected.equals(r.scene());
            case "메뉴" -> none ? r.food() == null : expected.equals(r.food());
            case "키워드" -> none ? r.keywords().isEmpty() : r.keywords().containsAll(Arrays.asList(expected.split(",")));
            case "실내외" -> none ? !r.keywords().contains("indoor") && !r.keywords().contains("outdoor") : r.keywords().contains(expected);
            case "예산" -> none ? r.keywords().stream().noneMatch(k -> k.startsWith("budget-")) : r.keywords().contains(expected);
            default -> throw new IllegalArgumentException(field);
        };
    }

    static String actual(String field, AiParseResponse r) {
        return switch (field) {
            case "지역" -> r.area() == null ? "-" : r.area().name() + "(" + r.area().kind() + ")";
            case "시도" -> r.sidos().isEmpty() ? "-" : String.join(",", r.sidos());
            case "풍경" -> r.scene() == null ? "-" : r.scene();
            case "메뉴" -> r.food() == null ? "-" : r.food();
            default -> r.keywords().isEmpty() ? "-" : String.join(",", r.keywords());
        };
    }

    static List<Case> load() throws IOException {
        try (InputStream in = AiParseEvalTests.class.getResourceAsStream("/ai-eval/queries.tsv")) {
            List<Case> cases = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] cols = line.strip().split("\t");
                assertThat(cols).as(line).hasSize(1 + FIELDS.size());
                boolean naverOnly = cols[0].startsWith("[네이버] ");
                String query = naverOnly ? cols[0].substring("[네이버] ".length()) : cols[0];
                cases.add(new Case(query, naverOnly, Arrays.copyOfRange(cols, 1, cols.length)));
            }
            return cases;
        }
    }
}
