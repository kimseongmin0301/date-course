package com.datecourse.domain.collector.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.datecourse.domain.collector.dto.PlaceCandidate;
import com.datecourse.domain.collector.util.Geo;
import com.datecourse.domain.collector.util.PlaceMatcher;
import com.datecourse.domain.collector.util.PlaceNames;
import com.datecourse.domain.place.entity.Place;
import com.datecourse.domain.place.entity.PlaceMergeCandidate;
import com.datecourse.domain.place.entity.PlaceSource;
import com.datecourse.domain.place.repository.PlaceMergeCandidateRepository;
import com.datecourse.domain.place.repository.PlaceRepository;
import com.datecourse.domain.place.repository.PlaceSourceRepository;

/**
 * 출처에서 읽은 장소 하나를 넣는다 (TourAPI 처럼 수가 적을 때. 많으면 SbizImporter 처럼 묶어서).
 *  1) 같은 출처·같은 원본 id 를 본 적 있으면 → 마지막으로 본 시각만 고친다
 *  2) 같은 장소 규칙(PlaceMatcher)으로 같은 장소면 → 출처만 더한다
 *  3) 그 밖에는 새 장소. 중복 후보가 있으면 place_merge_candidate 에 남긴다
 */
@Service
public class PlaceUpsertService {

    private final PlaceRepository places;
    private final PlaceSourceRepository sources;
    private final PlaceMergeCandidateRepository candidates;

    public PlaceUpsertService(PlaceRepository places, PlaceSourceRepository sources, PlaceMergeCandidateRepository candidates) {
        this.places = places;
        this.sources = sources;
        this.candidates = candidates;
    }

    public enum Result { SEEN_AGAIN, MERGED, CREATED, CREATED_WITH_CANDIDATE }

    @Transactional
    public Result upsert(PlaceCandidate c) {
        Optional<PlaceSource> known = sources.findBySourceAndExternalId(c.source(), c.externalId());
        if (known.isPresent()) {
            known.get().seen(c.rawName());
            fill(known.get().getPlace(), c);
            return Result.SEEN_AGAIN;
        }

        String normalized = PlaceNames.normalize(c.name());
        double dLat = Geo.latDelta(PlaceMatcher.CANDIDATE_M);
        double dLng = Geo.lngDelta(PlaceMatcher.CANDIDATE_M, c.lat());
        List<Place> sameName = places.findByNormalizedNameAndLatBetweenAndLngBetween(
                normalized, c.lat() - dLat, c.lat() + dLat, c.lng() - dLng, c.lng() + dLng);
        PlaceMatcher.Match match = PlaceMatcher.match(
                sameName.stream().map(p -> new PlaceMatcher.Known(p.getId(), p.getBranch(), p.getCategory(), p.getLat(), p.getLng())).toList(),
                c.branch(), c.category(), c.lat(), c.lng());

        if (match.sameId() != null) {
            Place same = sameName.stream().filter(p -> p.getId().equals(match.sameId())).findFirst().orElseThrow();
            sources.save(new PlaceSource(same, c.source(), c.externalId(), c.rawName()));
            fill(same, c);
            return Result.MERGED;
        }

        Place created = places.save(new Place(c.name(), c.branch(), normalized, c.category(), c.detailCategory(),
                c.lat(), c.lng(), c.sido(), c.sigungu(), c.dong()));
        fill(created, c);
        sources.save(new PlaceSource(created, c.source(), c.externalId(), c.rawName()));
        if (match.nearId() != null) {
            Place near = sameName.stream().filter(p -> p.getId().equals(match.nearId())).findFirst().orElseThrow();
            Place a = near.getId() < created.getId() ? near : created;
            Place b = a == near ? created : near;
            if (!candidates.existsByPlaceAAndPlaceB(a, b)) {
                candidates.save(new PlaceMergeCandidate(a, b, (int) Math.round(match.nearDistance())));
            }
            return Result.CREATED_WITH_CANDIDATE;
        }
        return Result.CREATED;
    }

    private static void fill(Place place, PlaceCandidate c) {
        place.fillMissing(c.dong(), c.roadAddress(), c.lotAddress(), c.phone(), c.imageUrl(), c.description());
    }
}
