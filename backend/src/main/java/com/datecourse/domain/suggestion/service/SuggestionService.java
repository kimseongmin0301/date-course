package com.datecourse.domain.suggestion.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.datecourse.domain.collector.util.Geo;
import com.datecourse.domain.collector.util.PlaceNames;
import com.datecourse.domain.collector.util.Regions;
import com.datecourse.domain.suggestion.repository.SuggestionRepository;
import com.datecourse.domain.suggestion.repository.SuggestionRepository.Row;

/**
 * 이용자가 알려 준 가게 (데이터에 없어 내 일정에 직접 넣은 곳).
 * 일정을 저장할 때 모으고, 바로 공개하지 않는다:
 *   - 같은 가게 = 정리한 이름이 같고 SAME_PLACE_M 안 (수집기의 중복 규칙과 같은 거리)
 *   - 서로 다른 방문자 PUBLIC_VISITORS 명이 같은 가게를 넣었거나 관리자가 승인하면 public → 모두의 장소 찾기에 나옴
 *   - 욕설·음란어 이름은 모으지 않고, 애매한 말(가리기 목록)이 든 이름은 review(관리자만 공개)
 *   - 이미 우리 장소 데이터에 있는 가게는 existing, 이미 공개된 가게는 merged
 *   - 한 방문자가 하루에 DAILY_MAX 개까지
 * 자동 코스·순위에는 쓰지 않는다.
 */
@Service
public class SuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);

    public static final double SAME_PLACE_M = 50;
    public static final int PUBLIC_VISITORS = 3;
    public static final int DAILY_MAX = 10;

    private final SuggestionRepository repository;
    private final ProfanityFilter profanity;

    public SuggestionService(SuggestionRepository repository, ProfanityFilter profanity) {
        this.repository = repository;
        this.profanity = profanity;
    }

    /** 일정에 직접 넣은 가게 하나를 제안으로 모은다. 모았으면 그 상태, 모으지 않았으면 비어 있다 */
    @Transactional
    public Optional<String> record(UUID visitor, String name, String address, double lat, double lng) {
        String normalized = PlaceNames.normalize(name);
        if (normalized.isEmpty() || profanity.find(name).isPresent()) {
            return Optional.empty();
        }
        List<Row> sameName = repository.byName(normalized);
        List<Row> samePlace = sameName.stream().filter(r -> near(r, lat, lng)).toList();
        if (samePlace.stream().anyMatch(r -> r.visitorId().equals(visitor))) {
            return Optional.empty();   // 이미 알려 준 가게 (일정을 다시 저장한 경우 등)
        }
        if (repository.countToday(visitor) >= DAILY_MAX) {
            return Optional.empty();
        }
        Regions.Region region = Regions.parse(address);
        String sido = region == null ? null : region.sido();
        String sigungu = region == null ? null : region.sigungu();

        Optional<Long> existing = repository.existingPlace(normalized, lat, lng, SAME_PLACE_M);
        if (existing.isPresent()) {
            repository.insert(visitor, name, normalized, address, lat, lng, sido, sigungu, "existing", null, existing.get());
            return Optional.of("existing");
        }
        Optional<Row> published = samePlace.stream().filter(r -> "public".equals(r.status())).findFirst();
        if (published.isPresent()) {
            repository.insert(visitor, name, normalized, address, lat, lng, sido, sigungu, "merged", published.get().id(), null);
            return Optional.of("merged");
        }
        boolean doubtful = !profanity.mask(name).equals(name);
        long id = repository.insert(visitor, name, normalized, address, lat, lng, sido, sigungu, doubtful ? "review" : "pending", null, null);
        if (doubtful) {
            return Optional.of("review");
        }
        // 이번 것까지 서로 다른 방문자가 PUBLIC_VISITORS 명이면 공개 (애매한 말이 든 review 는 세지 않는다)
        List<Row> pending = repository.byName(normalized).stream()
                .filter(r -> "pending".equals(r.status()) && near(r, lat, lng)).toList();
        long visitors = pending.stream().map(Row::visitorId).distinct().count();
        if (visitors >= PUBLIC_VISITORS) {
            publish(pending.stream().min(Comparator.comparingLong(Row::id)).orElseThrow(), pending);
            log.info("이용자 {}명이 알려 준 가게 공개: {}", visitors, normalized);
            return Optional.of("public");
        }
        return Optional.of(repository.find(id).map(Row::status).orElse("pending"));
    }

    /** 관리자 승인: 이 제안을 공개하고 같은 가게의 기다리는 제안은 합친다 */
    @Transactional
    public Row approve(long id) {
        Row row = repository.find(id).orElseThrow(() -> new IllegalArgumentException("없는 제안: " + id));
        List<Row> cluster = repository.byName(row.normalizedName()).stream()
                .filter(r -> near(r, row.lat(), row.lng()) && ("pending".equals(r.status()) || "review".equals(r.status()) || r.id() == id))
                .toList();
        publish(row, cluster);
        return repository.find(id).orElseThrow();
    }

    @Transactional
    public Row reject(long id) {
        repository.find(id).orElseThrow(() -> new IllegalArgumentException("없는 제안: " + id));
        repository.setStatus(id, "rejected", null);
        return repository.find(id).orElseThrow();
    }

    /** 관리자가 볼 목록: 기다리는 것(pending·review)과 공개된 것 */
    public List<Row> waiting(int limit) {
        return repository.byStatus(List.of("pending", "review", "public"), limit);
    }

    /** 장소 찾기에 섞을 공개된 가게 */
    public List<Row> publicNear(double lat, double lng, double meters, List<String> words) {
        return repository.publicNear(lat, lng, meters, words);
    }

    private void publish(Row target, List<Row> cluster) {
        repository.setStatus(target.id(), "public", null);
        for (Row r : cluster) {
            if (r.id() != target.id()) {
                repository.setStatus(r.id(), "merged", target.id());
            }
        }
    }

    private static boolean near(Row r, double lat, double lng) {
        return Geo.distanceMeters(r.lat(), r.lng(), lat, lng) <= SAME_PLACE_M;
    }
}
