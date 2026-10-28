package com.datecourse.domain.collector.util;

/** 주소 앞부분에서 시도·시군구를 꺼낸다 (TourAPI 는 시도·시군구 이름 칸이 없어서) */
public final class Regions {

    private Regions() {
    }

    /**
     * "서울특별시 강남구 테헤란로 1" → (서울특별시, 강남구)
     * "경기도 수원시 장안구 …" → (경기도, 수원시 장안구)  ← 상가정보의 시군구명과 같은 모양
     * "세종특별자치시 한누리대로 …" → (세종특별자치시, null)
     */
    public static Region parse(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String[] t = address.strip().split("\\s+");
        String sido = t[0];
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
