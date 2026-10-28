package com.datecourse.domain.collector.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 장소 이름 정리: 중복 찾기용 이름 만들기, 지점명 떼기 */
public final class PlaceNames {

    /** "OO점" 으로 끝나도 지점이 아닌 말 */
    private static final Set<String> NOT_BRANCH = Set.of("백화점", "면세점", "할인점", "매점", "상점", "서점", "편의점", "주점", "음식점", "전문점");
    /** "스타벅스 강남점": 띄어 쓴 마지막 낱말 */
    private static final Pattern SPACED_BRANCH = Pattern.compile("^(.*\\S)\\s+([^\\s()\\[\\]]+점)$");
    /** "스타벅스(강남점)", "스타벅스 [강남점]": 괄호 안 */
    private static final Pattern BRACKET_BRANCH = Pattern.compile("^(.*?\\S)\\s*[(\\[]\\s*([^\\s()\\[\\]]+점)\\s*[)\\]]$");

    private PlaceNames() {
    }

    /** 중복 찾기용: 소문자로, 글자·숫자만 남긴다 (공백·괄호·기호 제거) */
    public static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** 이름 끝의 지점명을 뗀다. 지점이 없으면 branch 는 null */
    public static Split splitBranch(String raw) {
        String name = raw == null ? "" : raw.strip();
        for (Pattern pattern : new Pattern[] {BRACKET_BRANCH, SPACED_BRANCH}) {
            Matcher m = pattern.matcher(name);
            if (m.matches() && !NOT_BRANCH.contains(m.group(2))) {
                return new Split(m.group(1).strip(), m.group(2));
            }
        }
        return new Split(name, null);
    }

    public record Split(String name, String branch) {
    }
}
