-- 장소 지정 정보 (공공기관이 지정한 품질 표시). 리뷰·별점 없이 품질을 가늠하는 신호로 코스 고르기·순위에 쓴다
--  - model_restaurant: 모범음식점 (행정안전부 전국모범음식점표준데이터, 지자체가 위생·서비스를 보고 지정)
-- 수집기가 원본 파일과 장소를 이름·주소로 맞춰 넣는다. 엔티티 없이 JDBC 로만 쓴다

SET search_path TO date_course;

CREATE TABLE place_badge (
    place_id       BIGINT      NOT NULL REFERENCES place (id) ON DELETE CASCADE,
    kind           VARCHAR(20) NOT NULL CHECK (kind IN ('model_restaurant')),
    source_id      VARCHAR(50),
    designated_on  DATE,
    seen_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (place_id, kind)
);
CREATE INDEX place_badge_kind_idx ON place_badge (kind);
COMMENT ON TABLE  place_badge               IS '공공기관이 지정한 장소 표시 (모범음식점 등). 코스 고르기·순위 가산점';
COMMENT ON COLUMN place_badge.kind          IS 'model_restaurant(모범음식점)';
COMMENT ON COLUMN place_badge.source_id     IS '원본 데이터의 관리번호';
COMMENT ON COLUMN place_badge.designated_on IS '지정일자';
COMMENT ON COLUMN place_badge.seen_at       IS '마지막으로 원본 파일에서 본 시각. 다시 넣을 때 안 보이면 지운다';
