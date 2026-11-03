-- 일정 공유 링크 (/s/{token}): 공유를 누른 때의 일정을 그대로 떠 두고, 링크를 아는 사람은 로그인 없이 본다.
--  steps 는 saved_plan.steps 와 같은 모양 (장소는 place id 만, 직접 넣은 가게는 이름·주소·좌표)
--  만든 사람(visitor_id)은 공유 화면에 내보내지 않고, 한 사람이 너무 많이 만들지 못하게 셀 때만 쓴다
--  만료(expires_at)가 지나면 열리지 않고, 매일 지운다

SET search_path TO date_course;

CREATE TABLE plan_share (
    token         VARCHAR(16) PRIMARY KEY,
    visitor_id    UUID        NOT NULL,
    course_id     BIGINT      NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    plan_date     DATE,
    start_minute  SMALLINT    NOT NULL CHECK (start_minute BETWEEN 0 AND 1439),
    steps         JSONB       NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX plan_share_visitor_idx ON plan_share (visitor_id, created_at DESC);
CREATE INDEX plan_share_expires_idx ON plan_share (expires_at);
COMMENT ON TABLE  plan_share            IS '일정 공유 링크: 공유할 때의 일정 그대로 (링크를 아는 사람만, 로그인 없이 읽기)';
COMMENT ON COLUMN plan_share.token      IS '공유 주소의 무작위 문자열 (10자, 대소문자·숫자)';
COMMENT ON COLUMN plan_share.visitor_id IS '만든 방문자 id (쿠키). 공유 화면에는 내보내지 않음';
COMMENT ON COLUMN plan_share.expires_at IS '이 시각이 지나면 열리지 않음 (만든 뒤 90일)';
