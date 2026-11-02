package com.datecourse.domain.ai.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.datecourse.domain.ai.client.OllamaClient;
import com.datecourse.domain.ai.repository.AiRepository;
import com.datecourse.domain.collector.util.Geo;

/**
 * 코스 임베딩: 코스를 글("제주시 도을 데이트 코스. 수눌음(기념품점) → 도을(카페) → …. 키워드: 감성, 실내")로 적어 벡터로 바꾼다.
 *   - 만들기(refresh): 수집기 명령(--collect=embeddings, 코스를 다시 만든 뒤에도)으로. 글이 바뀐 코스만. 32개씩(서버 GPU 로 약 0.7초)
 *   - 찾기(similar): 운영 앱은 DB 의 벡터를 메모리에 올려(1만여 개 × 768 ≈ 36MB) 요청 문장과 내적으로 비교한다. 6시간마다 다시 읽음
 * pgvector 없이 충분한 크기라 DB 확장을 설치하지 않았다.
 */
@Service
public class CourseEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(CourseEmbeddingService.class);
    static final int BATCH = 32;

    private final AiRepository repository;
    private final OllamaClient ollama;
    private volatile List<AiRepository.Stored> index = List.of();

    public CourseEmbeddingService(AiRepository repository, OllamaClient ollama) {
        this.repository = repository;
        this.ollama = ollama;
    }

    /** 임베딩할 글. 업종이 TourAPI 분류 코드(VE070600 등)면 우리 분류 이름을 쓴다 */
    static String text(AiRepository.CourseText c) {
        List<String> steps = new ArrayList<>();
        for (String s : c.steps()) {
            String[] p = s.split("\\|", -1);
            String detail = p.length > 2 && !p[2].isBlank() && !p[2].matches("[A-Z]{2}\\d.*") ? p[2] : CATEGORY.getOrDefault(p.length > 1 ? p[1] : "", "");
            steps.add(p[0] + (detail.isBlank() ? "" : "(" + detail + ")"));
        }
        String area = String.join(" ", java.util.stream.Stream.of(c.sido(), c.sigungu()).filter(v -> v != null && !v.isBlank()).toList());
        return area + " " + c.title() + ". " + String.join(" → ", steps) + "."
                + (c.keywords() == null || c.keywords().isBlank() ? "" : " 키워드: " + c.keywords() + ".");
    }

    private static final Map<String, String> CATEGORY = Map.of("attraction", "관광명소", "culture", "문화시설", "activity", "놀거리",
            "shop", "소품샵", "cafe", "카페", "restaurant", "식당", "bar", "술집");

    static String hash(String text) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 글이 바뀐(또는 없는) 코스만 다시 임베딩한다. 만든 수. Ollama 가 안 되면 0 (로그만) */
    public int refresh() {
        if (!ollama.enabled()) {
            log.warn("AI 가 꺼져 있어 코스 임베딩을 건너뜀");
            return 0;
        }
        long started = System.currentTimeMillis();
        String model = ollama.embedModel();
        Map<Long, String> have = repository.hashes(model);
        List<AiRepository.CourseText> todo = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (AiRepository.CourseText c : repository.courseTexts()) {
            String t = text(c);
            if (!hash(t).equals(have.get(c.id()))) {
                todo.add(c);
                texts.add(t);
            }
        }
        int done = 0;
        for (int from = 0; from < todo.size(); from += BATCH) {
            List<String> batch = texts.subList(from, Math.min(from + BATCH, todo.size()));
            List<float[]> vectors = ollama.embed(batch);
            if (vectors.isEmpty()) {
                log.warn("코스 임베딩 중단 ({}/{}): Ollama 에 닿지 않음", done, todo.size());
                break;
            }
            List<AiRepository.Row> rows = new ArrayList<>();
            for (int i = 0; i < vectors.size(); i++) {
                rows.add(new AiRepository.Row(todo.get(from + i).id(), hash(batch.get(i)), vectors.get(i)));
            }
            repository.upsertAll(model, rows);
            done += vectors.size();
            if (done % 1024 < BATCH) {
                log.info("코스 임베딩 {}/{}", done, todo.size());
            }
        }
        log.info("코스 임베딩 끝: {}개 새로 ({}초)", done, (System.currentTimeMillis() - started) / 1000);
        return done;
    }

    /** 운영 앱: 켜질 때 뒤에서, 그리고 6시간마다 DB 의 벡터를 메모리로 */
    @EventListener(ApplicationReadyEvent.class)
    public void loadInBackground() {
        CompletableFuture.runAsync(this::reload);
    }

    @Scheduled(cron = "0 15 */6 * * *", zone = "Asia/Seoul")
    public void reload() {
        try {
            index = List.copyOf(repository.loadAll(ollama.embedModel()));
            log.info("코스 임베딩 {}개를 메모리에 올림", index.size());
        } catch (RuntimeException e) {
            log.warn("코스 임베딩을 읽지 못했어요: {}", e.getMessage());
        }
    }

    public boolean ready() {
        return !index.isEmpty();
    }

    /** 요청 벡터와 가장 가까운 코스 id (가운데가 (lat, lng) 에서 meters 안, exclude 는 뺌) */
    public List<Long> similar(float[] query, double lat, double lng, double meters, Set<Long> exclude, int limit) {
        record Hit(long id, double score) {
        }
        return index.stream()
                .filter(s -> !exclude.contains(s.id()) && Geo.distanceMeters(lat, lng, s.lat(), s.lng()) <= meters)
                .map(s -> new Hit(s.id(), dot(query, s.vector())))
                .sorted(Comparator.comparingDouble(Hit::score).reversed())
                .limit(limit)
                .map(Hit::id)
                .toList();
    }

    static double dot(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    /** 테스트·색인 교체용 */
    void setIndex(List<AiRepository.Stored> stored) {
        index = List.copyOf(stored);
    }
}
