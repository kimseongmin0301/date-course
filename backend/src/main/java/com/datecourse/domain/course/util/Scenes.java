package com.datecourse.domain.course.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 풍경으로 코스 찾기 (말로 찾기의 "바다 보러", "산책할 숲", "호숫가"). 코스의 단계 장소 이름이나 업종이 이 정규식(PostgreSQL ~)에 맞으면 그 풍경 코스다.
 * 장소에는 풍경 칸이 따로 없어서 이름으로 본다: 바다는 해수욕장·해변·해안·포구·등대처럼 거의 틀리지 않는 말만 쓴다.
 */
public final class Scenes {

    private Scenes() {
    }

    public record Scene(String code, String name, String regex) {
    }

    public static final Map<String, Scene> ALL = new LinkedHashMap<>();

    static {
        add(new Scene("sea", "바다", "해수욕장|해변|바다|바닷|해안|포구|등대|비치|오션|마리나|방파제|해상|갯벌|모래사장|해맞이"));
        add(new Scene("mountain", "산·숲", "오름|숲|수목원|휴양림|산림|둘레길|등산로|[가-힣]{1,4}산$|산성$"));
        add(new Scene("water", "강·호수", "호수|저수지|계곡|강변|한강|천변|폭포|수변|생태공원|강가"));
    }

    private static void add(Scene s) {
        ALL.put(s.code(), s);
    }

    /** 코드 → 풍경 (모르는 코드나 null 은 null) */
    public static Scene of(String code) {
        return code == null ? null : ALL.get(code);
    }
}
