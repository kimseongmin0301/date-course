package com.datecourse.domain.collector.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.datecourse.domain.collector.dto.PlaceCandidate;
import com.datecourse.domain.collector.util.Geo;
import com.datecourse.domain.collector.util.PlaceNames;
import com.datecourse.domain.place.entity.Place;
import com.datecourse.domain.place.entity.PlaceMergeCandidate;
import com.datecourse.domain.place.entity.PlaceSource;
import com.datecourse.domain.place.repository.PlaceMergeCandidateRepository;
import com.datecourse.domain.place.repository.PlaceRepository;
import com.datecourse.domain.place.repository.PlaceSourceRepository;

/**
 * 출처에서 읽은 장소 하나를 넣는다.
 *  1) 같은 출처·같은 원본 id 를 본 적 있으면 → 마지막으로 본 시각만 고친다
 *  2) 정리한 이름이 같고 50m 안 → 같은 장소로 보고 출처만 더한다
 *  3) 그 밖에는 새 장소. 이름이 같은데 50~150m 면 중복 후보로 남겨 관리자가 정한다
 */
@Service
public class PlaceUpsertService {

    static final double SAME_PLACE_M = 50;
    static final double CANDIDATE_M = 150;

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
        double dLat = Geo.latDelta(CANDIDATE_M);
        double dLng = Geo.lngDelta(CANDIDATE_M, c.lat());
        List<Place> sameName = places.findByNormalizedNameAndLatBetweenAndLngBetween(
                normalized, c.lat() - dLat, c.lat() + dLat, c.lng() - dLng, c.lng() + dLng);

        Place same = null;
        Place near = null;
        double sameDistance = Double.MAX_VALUE;
        double nearDistance = Double.MAX_VALUE;
        for (Place p : sameName) {
            if (differentBranch(p.getBranch(), c.branch())) {
                continue;
            }
            double d = Geo.distanceMeters(p.getLat(), p.getLng(), c.lat(), c.lng());
            if (d <= SAME_PLACE_M && d < sameDistance) {
                same = p;
                sameDistance = d;
            } else if (d <= CANDIDATE_M && d < nearDistance) {
                near = p;
                nearDistance = d;
            }
        }

        if (same != null) {
            sources.save(new PlaceSource(same, c.source(), c.externalId(), c.rawName()));
            fill(same, c);
            return Result.MERGED;
        }

        Place created = places.save(new Place(c.name(), c.branch(), normalized, c.category(), c.detailCategory(),
                c.lat(), c.lng(), c.sido(), c.sigungu(), c.dong()));
        fill(created, c);
        sources.save(new PlaceSource(created, c.source(), c.externalId(), c.rawName()));
        if (near != null) {
            Place a = near.getId() < created.getId() ? near : created;
            Place b = a == near ? created : near;
            if (!candidates.existsByPlaceAAndPlaceB(a, b)) {
                candidates.save(new PlaceMergeCandidate(a, b, (int) Math.round(nearDistance)));
            }
            return Result.CREATED_WITH_CANDIDATE;
        }
        return Result.CREATED;
    }

    /** 둘 다 지점명이 있는데 다르면 다른 가게 (예: 강남점 ↔ 역삼점) */
    static boolean differentBranch(String a, String b) {
        return a != null && b != null && !Objects.equals(PlaceNames.normalize(a), PlaceNames.normalize(b));
    }

    private static void fill(Place place, PlaceCandidate c) {
        place.fillMissing(c.roadAddress(), c.lotAddress(), c.phone(), c.imageUrl(), c.description());
    }
}
