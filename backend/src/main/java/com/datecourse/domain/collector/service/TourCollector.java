package com.datecourse.domain.collector.service;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.datecourse.domain.collector.client.TourApiClient;
import com.datecourse.domain.collector.dto.PlaceCandidate;

import tools.jackson.databind.JsonNode;

/**
 * TourAPI 의 관광지·문화시설·레포츠·음식점을 모은다.
 * 한 번에 1,000개씩 받으므로 전국을 모아도 호출은 40번 남짓 (개발 계정 하루 한도 안)
 */
@Service
public class TourCollector {

    private static final Logger log = LoggerFactory.getLogger(TourCollector.class);
    private static final int ROWS = 1000;

    private final TourApiClient client;
    private final PlaceUpsertService upsert;

    public TourCollector(TourApiClient client, PlaceUpsertService upsert) {
        this.client = client;
        this.upsert = upsert;
    }

    /** areaCode 가 null 이면 전국 (1 서울, 6 부산 …) */
    public Map<PlaceUpsertService.Result, Integer> collect(Integer areaCode) {
        if (!client.configured()) {
            throw new IllegalStateException("TOUR_API_SERVICE_KEY 가 비어 있습니다 (backend/.env)");
        }
        Map<PlaceUpsertService.Result, Integer> counts = new EnumMap<>(PlaceUpsertService.Result.class);
        int skipped = 0;
        for (int contentType : TourItemMapper.CONTENT_TYPES) {
            int pageNo = 1;
            int read = 0;
            TourApiClient.Page page;
            do {
                page = client.areaBasedList(contentType, areaCode, pageNo, ROWS);
                for (JsonNode item : page.items()) {
                    Optional<PlaceCandidate> candidate = TourItemMapper.toCandidate(item);
                    if (candidate.isEmpty()) {
                        skipped++;
                        continue;
                    }
                    counts.merge(upsert.upsert(candidate.get()), 1, Integer::sum);
                }
                read += page.items().size();
                log.info("TourAPI 관광타입 {} : {}/{}", contentType, read, page.totalCount());
                pageNo++;
            } while (!page.items().isEmpty() && read < page.totalCount());
        }
        log.info("TourAPI 수집 끝 (지역 {}): {}, 건너뜀 {}", areaCode == null ? "전국" : areaCode, counts, skipped);
        return counts;
    }
}
