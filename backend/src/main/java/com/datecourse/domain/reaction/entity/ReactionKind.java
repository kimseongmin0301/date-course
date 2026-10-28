package com.datecourse.domain.reaction.entity;

import com.datecourse.global.util.LowercaseEnumConverter;

import jakarta.persistence.Converter;

/** 코스 반응 종류 (reaction.kind). 순위 점수는 VISITED > SAVE > LIKE */
public enum ReactionKind {
    /** 저장 */
    SAVE,
    /** 좋아요 */
    LIKE,
    /** 다녀왔어요 */
    VISITED;

    @Converter(autoApply = true)
    public static class DbConverter extends LowercaseEnumConverter<ReactionKind> {
        public DbConverter() {
            super(ReactionKind.class);
        }
    }
}
