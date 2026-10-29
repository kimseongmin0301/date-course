-- 내 일정 저장 (로그인 없음). 브라우저 쿠키(dc_vid)의 방문자 id 로 구분하고, 코스마다 하나씩 둔다
--  steps: [{ "placeId", "role", "stay", "fixed", "origin", "courseOrder" }]
--    role: spot/cafe/meal/mine, stay: 머무는 시간(분), fixed: 도착 시간을 고정한 경우 그날 0시부터 분(없으면 null),
--    origin: course(원래 그대로)/swap(바꿈)/added(내가 넣음), courseOrder: 원래 코스의 몇 번째 단계였는지(더한 곳은 null)
--  장소 이름·주소는 두지 않고 place id 만 둔다 (읽을 때 place 와 이어 붙인다)

SET search_path TO date_course;

CREATE TABLE saved_plan (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    visitor_id    UUID        NOT NULL,
    course_id     BIGINT      NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    plan_date     DATE,
    start_minute  SMALLINT    NOT NULL CHECK (start_minute BETWEEN 0 AND 1439),
    steps         JSONB       NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (visitor_id, course_id)
);
CREATE INDEX saved_plan_visitor_idx ON saved_plan (visitor_id, updated_at DESC);
COMMENT ON TABLE  saved_plan              IS '방문자가 저장한 내 일정 (코스를 바꾸고 더한 단계와 시간표). 방문자마다 코스 하나에 하나';
COMMENT ON COLUMN saved_plan.visitor_id   IS '방문자 id (쿠키 dc_vid, 브라우저마다 만드는 UUID). 사람을 알아볼 수 있는 정보는 두지 않는다';
COMMENT ON COLUMN saved_plan.start_minute IS '출발 시간: 그날 0시부터 분 (0~1439)';
COMMENT ON COLUMN saved_plan.steps        IS '단계 목록 JSON: placeId, role, stay(분), fixed(고정 도착, 분 또는 null), origin, courseOrder';
