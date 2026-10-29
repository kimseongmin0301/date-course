package com.datecourse.domain.collector.util;

import java.util.Arrays;

/**
 * 주소를 맞춰 보기 위한 열쇠: 시도를 떼고, 쉼표 뒤(층·호)와 괄호(동 이름·건물명)를 뗀 "시군구 길 번호".
 *   "서울특별시 종로구 삼청로 54, 지하1,지상1,2층 (소격동)" → "종로구 삼청로 54"
 *   "서울특별시 종로구 소격동 48 "                          → "종로구 소격동 48"
 * 시도는 출처마다 이름이 달라서 뗀다 (강원도 ↔ 강원특별자치도, 광주광역시 ↔ 전남광주통합특별시).
 */
public final class AddressKey {

    private AddressKey() {
    }

    /** 맞춰 볼 수 없으면 null (시군구 + 길/동 + 번호 세 토막이 안 되면) */
    public static String of(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String a = address.strip();
        int comma = a.indexOf(',');
        if (comma > 0) {
            a = a.substring(0, comma);
        }
        int paren = a.indexOf('(');
        if (paren > 0) {
            a = a.substring(0, paren);
        }
        String[] tokens = a.strip().split("\\s+");
        if (tokens.length < 4) {
            return null;
        }
        return String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length));
    }
}
