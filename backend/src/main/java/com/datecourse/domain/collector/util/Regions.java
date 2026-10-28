package com.datecourse.domain.collector.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** 주소 앞부분에서 시도·시군구를 꺼낸다 (TourAPI 는 시도·시군구 이름 칸이 없어서) */
public final class Regions {

    /**
     * 주소 첫 낱말의 앞부분 → 공식 시도 이름 (상가정보 시도명과 같게).
     * TourAPI 주소는 "경남", "서울", "광주광역사" 처럼 줄이거나 틀리게 쓴 것이 섞여 있다
     */
    private static final Map<String, String> SIDO = new LinkedHashMap<>();

    static {
        SIDO.put("서울", "서울특별시");
        SIDO.put("부산", "부산광역시");
        SIDO.put("대구", "대구광역시");
        SIDO.put("인천", "인천광역시");
        SIDO.put("대전", "대전광역시");
        SIDO.put("울산", "울산광역시");
        SIDO.put("세종", "세종특별자치시");
        SIDO.put("경기", "경기도");
        SIDO.put("강원", "강원특별자치도");
        SIDO.put("충북", "충청북도");
        SIDO.put("충청북", "충청북도");
        SIDO.put("충남", "충청남도");
        SIDO.put("충청남", "충청남도");
        SIDO.put("전북", "전북특별자치도");
        SIDO.put("전라북", "전북특별자치도");
        // 2026 년 전남·광주가 전남광주통합특별시로 합쳐짐
        SIDO.put("전남", "전남광주통합특별시");
        SIDO.put("전라남", "전남광주통합특별시");
        SIDO.put("광주", "전남광주통합특별시");
        SIDO.put("경북", "경상북도");
        SIDO.put("경상북", "경상북도");
        SIDO.put("경남", "경상남도");
        SIDO.put("경상남", "경상남도");
        SIDO.put("제주", "제주특별자치도");
    }

    private Regions() {
    }

    /** "경남", "경상남도", "경남도" → 경상남도. 모르는 말이면 null */
    public static String canonicalSido(String token) {
        if (token == null) {
            return null;
        }
        for (Map.Entry<String, String> e : SIDO.entrySet()) {
            if (token.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * "서울특별시 강남구 테헤란로 1" → (서울특별시, 강남구)
     * "경기도 수원시 장안구 …" → (경기도, 수원시 장안구)  ← 상가정보의 시군구명과 같은 모양
     * "경남 사천시 …" → (경상남도, 사천시)
     * "세종특별자치시 한누리대로 …" → (세종특별자치시, null)
     * 시도를 알 수 없으면 null
     */
    public static Region parse(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String[] t = address.strip().split("\\s+");
        String sido = canonicalSido(t[0]);
        if (sido == null) {
            return null;
        }
        String sigungu = null;
        if (t.length > 1 && isSigungu(t[1])) {
            sigungu = t[1];
            if (t[1].endsWith("시") && t.length > 2 && t[2].endsWith("구")) {
                sigungu = t[1] + " " + t[2];
            }
        }
        return new Region(sido, sigungu);
    }

    private static boolean isSigungu(String token) {
        return token.length() >= 2 && (token.endsWith("시") || token.endsWith("군") || token.endsWith("구"));
    }

    public record Region(String sido, String sigungu) {
    }
}
