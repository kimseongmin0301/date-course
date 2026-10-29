-- 이용자가 알려 준 가게 (데이터에 없어 내 일정에 "이름과 주소로 직접 넣기"로 넣은 곳)
--  일정을 저장할 때 직접 넣은 가게를 제안으로 모은다. 바로 공개하지 않고,
--  서로 다른 방문자 3명이 같은 가게(같은 정리한 이름, 50m 안)를 넣었거나 관리자가 승인하면 public 이 되어
--  모두의 "장소 찾기"에 나온다. 자동 코스·순위에는 쓰지 않는다.
--  status: pending(모으는 중) / review(애매한 말이 있어 관리자만 공개) / public(공개) /
--          merged(같은 가게의 다른 제안에 합침, merged_into) / existing(이미 우리 장소 데이터에 있음, place_id) / rejected(관리자가 거절)

SET search_path TO date_course;

CREATE TABLE place_suggestion (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    visitor_id      UUID             NOT NULL,
    name            VARCHAR(30)      NOT NULL,
    normalized_name VARCHAR(60)      NOT NULL,
    address         VARCHAR(100)     NOT NULL,
    lat             DOUBLE PRECISION NOT NULL,
    lng             DOUBLE PRECISION NOT NULL,
    sido            VARCHAR(20),
    sigungu         VARCHAR(30),
    status          VARCHAR(10)      NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'review', 'public', 'merged', 'existing', 'rejected')),
    merged_into     BIGINT REFERENCES place_suggestion (id) ON DELETE SET NULL,
    place_id        BIGINT REFERENCES place (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    decided_at      TIMESTAMPTZ
);
CREATE INDEX place_suggestion_name_idx ON place_suggestion (normalized_name);
CREATE INDEX place_suggestion_public_idx ON place_suggestion (lat, lng) WHERE status = 'public';
CREATE INDEX place_suggestion_visitor_idx ON place_suggestion (visitor_id, created_at DESC);
COMMENT ON TABLE  place_suggestion                 IS '이용자가 알려 준 가게 (데이터에 없어 직접 넣은 곳). 3명이 넣었거나 관리자가 승인하면 장소 찾기에 공개';
COMMENT ON COLUMN place_suggestion.visitor_id      IS '알려 준 방문자 id (쿠키 dc_vid). 서로 다른 사람 수를 세는 데만 쓰고 밖으로 내보내지 않는다';
COMMENT ON COLUMN place_suggestion.normalized_name IS '같은 가게 비교용 이름 (PlaceNames.normalize: 소문자, 글자·숫자만)';
COMMENT ON COLUMN place_suggestion.status          IS 'pending / review / public / merged / existing / rejected';
