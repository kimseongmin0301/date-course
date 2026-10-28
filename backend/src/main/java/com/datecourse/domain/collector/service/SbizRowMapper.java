package com.datecourse.domain.collector.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.datecourse.domain.collector.dto.PlaceCandidate;
import com.datecourse.domain.collector.util.Geo;
import com.datecourse.domain.collector.util.PlaceNames;
import com.datecourse.domain.place.entity.PlaceCategory;
import com.datecourse.domain.place.entity.PlaceSourceType;

/**
 * 상가(상권)정보 CSV 한 줄 → 장소 후보. 데이트에 쓸 업종만 남긴다 (서울 기준 55만 줄 중 약 16만).
 * 열은 이름으로 찾는다 (분기마다 열 순서가 바뀌어도 되게)
 */
public final class SbizRowMapper {

    /** 중분류 전체를 한 분류로 (상권업종중분류명, 끝 공백은 지운 뒤) */
    private static final Map<String, PlaceCategory> BY_MIDDLE = Map.of(
            "한식", PlaceCategory.RESTAURANT,
            "일식", PlaceCategory.RESTAURANT,
            "중식", PlaceCategory.RESTAURANT,
            "서양식", PlaceCategory.RESTAURANT,
            "동남아시아", PlaceCategory.RESTAURANT,
            "기타 외국", PlaceCategory.RESTAURANT);

    /** 소분류 하나씩 (상권업종소분류명) */
    private static final Map<String, PlaceCategory> BY_SMALL = new HashMap<>();

    static {
        for (String s : List.of("카페", "빵/도넛", "아이스크림/빙수")) {
            BY_SMALL.put(s, PlaceCategory.CAFE);
        }
        for (String s : List.of("김밥/만두/분식", "치킨", "피자", "버거", "토스트/샌드위치/샐러드", "그 외 기타 간이 음식점", "뷔페")) {
            BY_SMALL.put(s, PlaceCategory.RESTAURANT);
        }
        // 유흥 주점(일반·무도)은 뺀다
        for (String s : List.of("요리 주점", "생맥주 전문")) {
            BY_SMALL.put(s, PlaceCategory.BAR);
        }
        for (String s : List.of("기념품점", "예술품 소매업", "꽃집", "액세서리/잡화 소매업", "문구/회화용품 소매업", "서점",
                "장난감 소매업", "음반/비디오물 소매업")) {
            BY_SMALL.put(s, PlaceCategory.SHOP);
        }
        for (String s : List.of("노래방", "기타 오락장", "전자 게임장", "볼링장", "당구장", "기타 오락관련 서비스업", "수상/해양 레저업")) {
            BY_SMALL.put(s, PlaceCategory.ACTIVITY);
        }
    }

    static final Set<String> REQUIRED = Set.of("상가업소번호", "상호명", "지점명", "상권업종중분류명", "상권업종소분류명",
            "시도명", "시군구명", "행정동명", "도로명주소", "지번주소", "경도", "위도");

    private final Map<String, Integer> index = new HashMap<>();

    public SbizRowMapper(String[] header) {
        for (int i = 0; i < header.length; i++) {
            index.put(header[i].replace("﻿", "").strip(), i);
        }
        for (String name : REQUIRED) {
            if (!index.containsKey(name)) {
                throw new IllegalArgumentException("상가정보 CSV 에 열이 없습니다: " + name);
            }
        }
    }

    static PlaceCategory category(String middle, String small) {
        PlaceCategory bySmall = BY_SMALL.get(small == null ? "" : small.strip());
        return bySmall != null ? bySmall : BY_MIDDLE.get(middle == null ? "" : middle.strip());
    }

    public Optional<PlaceCandidate> toCandidate(String[] row) {
        PlaceCategory category = category(get(row, "상권업종중분류명"), get(row, "상권업종소분류명"));
        String id = get(row, "상가업소번호");
        String title = get(row, "상호명");
        String sido = get(row, "시도명");
        double lat = number(get(row, "위도"));
        double lng = number(get(row, "경도"));
        if (category == null || id == null || title == null || sido == null || !Geo.inKorea(lat, lng)) {
            return Optional.empty();
        }
        String branch = get(row, "지점명");
        PlaceNames.Split split = branch != null ? new PlaceNames.Split(title, branch) : PlaceNames.splitBranch(title);
        return Optional.of(new PlaceCandidate(
                PlaceSourceType.SBIZ, id, branch != null ? title + " " + branch : title,
                split.name(), split.branch(), category, get(row, "상권업종소분류명"),
                lat, lng, sido, get(row, "시군구명"), get(row, "행정동명"),
                get(row, "도로명주소"), get(row, "지번주소"), null, null, null));
    }

    private String get(String[] row, String column) {
        int i = index.get(column);
        if (i >= row.length) {
            return null;
        }
        String v = row[i].strip();
        return v.isEmpty() ? null : v;
    }

    private static double number(String v) {
        try {
            return v == null ? 0 : Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
