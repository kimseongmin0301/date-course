package com.datecourse.domain.collector.service;

import java.util.Optional;

import com.datecourse.domain.collector.dto.PlaceCandidate;
import com.datecourse.domain.collector.util.Geo;
import com.datecourse.domain.collector.util.PlaceNames;
import com.datecourse.domain.collector.util.Regions;
import com.datecourse.domain.place.entity.PlaceCategory;
import com.datecourse.domain.place.entity.PlaceSourceType;

import tools.jackson.databind.JsonNode;

/** TourAPI 목록의 항목 하나 → 우리 장소 후보 */
public final class TourItemMapper {

    /** 관광타입: 12 관광지, 14 문화시설, 28 레포츠, 39 음식점 */
    public static final int[] CONTENT_TYPES = {12, 14, 28, 39};

    private TourItemMapper() {
    }

    public static Optional<PlaceCandidate> toCandidate(JsonNode item) {
        String id = text(item, "contentid");
        String title = text(item, "title");
        double lat = number(item, "mapy");
        double lng = number(item, "mapx");
        Regions.Region region = Regions.parse(text(item, "addr1"));
        PlaceCategory category = category(item);
        if (id == null || title == null || region == null || category == null || !Geo.inKorea(lat, lng)) {
            return Optional.empty();
        }
        PlaceNames.Split split = PlaceNames.splitBranch(title);
        String address = text(item, "addr1");
        boolean road = address != null && address.matches(".*(로|길)\\s*\\d.*");
        return Optional.of(new PlaceCandidate(
                PlaceSourceType.TOUR, id, title, split.name(), split.branch(), category,
                firstNonNull(text(item, "lclsSystm3"), text(item, "cat3")),
                lat, lng, region.sido(), region.sigungu(), null,
                road ? address : null, road ? null : address,
                phone(text(item, "tel")), text(item, "firstimage"), null));
    }

    /**
     * 12 → 관광명소, 14 → 문화시설, 28 → 체험·놀거리, 39 → 음식점.
     * 음식점 중 분류 FD05(카페·전통찻집)와 cat3 A05020900(카페/전통찻집, 빵집 카페 포함)은 카페로
     */
    static PlaceCategory category(JsonNode item) {
        return switch (text(item, "contenttypeid") == null ? "" : text(item, "contenttypeid")) {
            case "12" -> PlaceCategory.ATTRACTION;
            case "14" -> PlaceCategory.CULTURE;
            case "28" -> PlaceCategory.ACTIVITY;
            case "39" -> "FD05".equals(text(item, "lclsSystm2")) || "A05020900".equals(text(item, "cat3"))
                    ? PlaceCategory.CAFE : PlaceCategory.RESTAURANT;
            default -> null;
        };
    }

    /** "02-123-4567<br>02-…" 처럼 여러 개거나 태그가 섞여 오면 첫 번호만 */
    static String phone(String raw) {
        if (raw == null) {
            return null;
        }
        String first = raw.replaceAll("<[^>]*>", "\n").split("[\\n,/]")[0].strip();
        if (first.isEmpty()) {
            return null;
        }
        return first.length() > 40 ? first.substring(0, 40) : first;
    }

    private static String text(JsonNode item, String field) {
        String v = item.path(field).asString("").strip();
        return v.isEmpty() ? null : v;
    }

    private static double number(JsonNode item, String field) {
        try {
            return Double.parseDouble(item.path(field).asString("0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
