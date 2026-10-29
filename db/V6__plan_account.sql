-- 다른 기기에서도 내 일정 보기 (고를 때만, 이메일·전화 없음).
--  "내 저장"에서 비밀번호를 정하면 서버가 코드(예: K7QM-29XD)를 만들어 지금 브라우저의 방문자 id 와 묶는다.
--  다른 기기에서 코드 + 비밀번호를 넣으면 그 브라우저에 저장된 일정을 이 방문자 id 로 옮기고 쿠키(dc_vid)도 이 id 로 바꾼다.
--  비밀번호는 원문 없이 해시만 (pbkdf2_sha256$반복$소금$해시). 잊으면 되찾을 수 없다(연락처를 모으지 않아서).

SET search_path TO date_course;

CREATE TABLE plan_account (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code           CHAR(8)      NOT NULL UNIQUE,
    visitor_id     UUID         NOT NULL UNIQUE,
    password_hash  VARCHAR(200) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at  TIMESTAMPTZ
);
COMMENT ON TABLE  plan_account               IS '다른 기기에서도 내 일정 보기: 코드 + 비밀번호 → 방문자 id. 이메일·전화 같은 연락처는 두지 않는다';
COMMENT ON COLUMN plan_account.code          IS '로그인 코드 8자 (헷갈리는 0·O·1·I 를 뺀 대문자·숫자). 화면은 4자씩 끊어 보여줌';
COMMENT ON COLUMN plan_account.visitor_id    IS '이 코드로 로그인하면 쿠키 dc_vid 에 넣을 방문자 id (saved_plan.visitor_id 와 같은 값)';
COMMENT ON COLUMN plan_account.password_hash IS 'pbkdf2_sha256$반복횟수$소금$해시 (원문 비밀번호는 두지 않음)';
