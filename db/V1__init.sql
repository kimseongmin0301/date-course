-- 데이트코스 추천: 첫 스키마
--  - place: 상가(상권)정보·TourAPI 에서 모아 중복을 합친 장소. 출처별 원본 id 는 place_source
--  - keyword: 분위기·활동·실내외·예산 키워드. 장소·코스에 붙인다
--  - course / course_step: 데이트 코스(관리자 큐레이션 또는 자동 조합)와 단계별 장소
--  - reaction: 방문자(로그인 없음, 브라우저마다 UUID)의 저장·좋아요·다녀왔어요. 순위 점수에 쓴다
-- 좌표는 PostGIS 없이 위도·경도 칸으로 두고, 주변 찾기는 위경도 범위(사각형)로 먼저 거른 뒤 거리를 계산한다
-- 힐링룸과 같은 DB(healing_room)·계정을 쓰고, 테이블은 전용 스키마 date_course 에만 만든다 (힐링룸은 public)

CREATE SCHEMA date_course;
COMMENT ON SCHEMA date_course IS '데이트코스 추천 (date-course 저장소). 힐링룸 테이블은 public';
SET search_path TO date_course;

-- 1) 장소
CREATE TABLE place (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name             VARCHAR(200)     NOT NULL,
    branch           VARCHAR(100),
    normalized_name  VARCHAR(200)     NOT NULL,
    category         VARCHAR(20)      NOT NULL CHECK (category IN ('attraction', 'culture', 'activity', 'shop', 'cafe', 'restaurant', 'bar')),
    detail_category  VARCHAR(100),
    lat              DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN 33 AND 39),
    lng              DOUBLE PRECISION NOT NULL CHECK (lng BETWEEN 124 AND 132),
    sido             VARCHAR(20)      NOT NULL,
    sigungu          VARCHAR(30),
    dong             VARCHAR(30),
    road_address     VARCHAR(300),
    lot_address      VARCHAR(300),
    phone            VARCHAR(40),
    image_url        VARCHAR(500),
    description      TEXT,
    closed_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ      NOT NULL DEFAULT now()
);
CREATE INDEX place_location_idx ON place (lat, lng) WHERE closed_at IS NULL;
CREATE INDEX place_region_idx   ON place (sido, sigungu, dong) WHERE closed_at IS NULL;
CREATE INDEX place_name_idx     ON place (normalized_name);
COMMENT ON TABLE  place                 IS '장소. 여러 출처에서 모아 같은 장소는 하나로 합친다';
COMMENT ON COLUMN place.name            IS '보여줄 이름 (지점명 제외)';
COMMENT ON COLUMN place.branch          IS '지점명 (예: 강남점). 없으면 NULL';
COMMENT ON COLUMN place.normalized_name IS '중복 찾기용 이름: 공백·괄호·기호를 빼고 소문자로';
COMMENT ON COLUMN place.category        IS '우리 분류: attraction(관광명소), culture(전시·공연), activity(체험·놀거리), shop(소품샵 등), cafe, restaurant, bar';
COMMENT ON COLUMN place.detail_category IS '출처의 원래 업종·분류 이름 (예: 커피전문점)';
COMMENT ON COLUMN place.lat             IS '위도 (WGS84)';
COMMENT ON COLUMN place.lng             IS '경도 (WGS84)';
COMMENT ON COLUMN place.sido            IS '시도 (예: 서울특별시). 지역 검색에 쓴다';
COMMENT ON COLUMN place.sigungu         IS '시군구 (예: 강남구)';
COMMENT ON COLUMN place.dong            IS '행정동 (예: 역삼1동)';
COMMENT ON COLUMN place.image_url       IS '대표 사진 (TourAPI)';
COMMENT ON COLUMN place.description     IS '소개 글 (TourAPI)';
COMMENT ON COLUMN place.closed_at       IS '폐업·사라짐으로 본 시각 (최근 수집에서 모든 출처에 없으면). 영업 중이면 NULL';

CREATE TABLE place_source (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    place_id      BIGINT       NOT NULL REFERENCES place (id) ON DELETE CASCADE,
    source        VARCHAR(10)  NOT NULL CHECK (source IN ('sbiz', 'tour')),
    external_id   VARCHAR(50)  NOT NULL,
    raw_name      VARCHAR(200) NOT NULL,
    last_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (source, external_id)
);
CREATE INDEX place_source_place_idx ON place_source (place_id);
COMMENT ON TABLE  place_source              IS '장소의 출처별 원본. 한 장소에 출처가 여럿이면 "출처 일치" 점수가 오른다';
COMMENT ON COLUMN place_source.source       IS 'sbiz(소상공인 상가정보), tour(한국관광공사 TourAPI)';
COMMENT ON COLUMN place_source.external_id  IS '출처의 id (상가업소번호, contentid)';
COMMENT ON COLUMN place_source.raw_name     IS '출처에 적힌 이름 그대로';
COMMENT ON COLUMN place_source.last_seen_at IS '마지막으로 수집에서 본 시각. 오래 안 보이면 폐업으로 본다';

CREATE TABLE place_merge_candidate (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    place_a_id  BIGINT      NOT NULL REFERENCES place (id) ON DELETE CASCADE,
    place_b_id  BIGINT      NOT NULL REFERENCES place (id) ON DELETE CASCADE,
    distance_m  INTEGER     NOT NULL,
    status      VARCHAR(10) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'merged', 'different')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at  TIMESTAMPTZ,
    CHECK (place_a_id < place_b_id),
    UNIQUE (place_a_id, place_b_id)
);
CREATE INDEX place_merge_candidate_pending_idx ON place_merge_candidate (created_at) WHERE status = 'pending';
COMMENT ON TABLE  place_merge_candidate        IS '같은 장소인지 애매한 쌍 (이름이 비슷하고 50~150m). 관리자가 합치거나 다르다고 정한다';
COMMENT ON COLUMN place_merge_candidate.status IS 'pending(기다림), merged(합침), different(다른 장소)';

-- 2) 키워드
CREATE TABLE keyword (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(30) NOT NULL UNIQUE,
    name        VARCHAR(30) NOT NULL,
    kind        VARCHAR(10) NOT NULL CHECK (kind IN ('mood', 'activity', 'setting', 'budget')),
    sort_order  INTEGER     NOT NULL DEFAULT 0
);
COMMENT ON TABLE  keyword      IS '코스·장소에 붙이는 키워드';
COMMENT ON COLUMN keyword.code IS '주소·API 에 쓰는 영문 id (예: quiet)';
COMMENT ON COLUMN keyword.name IS '화면에 보여줄 이름 (예: 조용)';
COMMENT ON COLUMN keyword.kind IS 'mood(분위기), activity(활동), setting(실내외: 하나만), budget(예산: 하나만)';

CREATE TABLE place_keyword (
    place_id    BIGINT      NOT NULL REFERENCES place (id) ON DELETE CASCADE,
    keyword_id  BIGINT      NOT NULL REFERENCES keyword (id) ON DELETE CASCADE,
    origin      VARCHAR(10) NOT NULL CHECK (origin IN ('auto', 'admin')),
    PRIMARY KEY (place_id, keyword_id)
);
CREATE INDEX place_keyword_keyword_idx ON place_keyword (keyword_id);
COMMENT ON COLUMN place_keyword.origin IS 'auto(분류로 자동으로 붙임), admin(관리자가 붙임: 자동 수집이 덮어쓰지 않음)';

-- 3) 코스
CREATE TABLE course (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title             VARCHAR(100)     NOT NULL,
    summary           VARCHAR(300),
    kind              VARCHAR(10)      NOT NULL CHECK (kind IN ('curated', 'auto')),
    anchor_place_id   BIGINT           REFERENCES place (id) ON DELETE SET NULL,
    sido              VARCHAR(20)      NOT NULL,
    sigungu           VARCHAR(30),
    center_lat        DOUBLE PRECISION NOT NULL,
    center_lng        DOUBLE PRECISION NOT NULL,
    total_minutes     INTEGER          CHECK (total_minutes > 0),
    budget_min        INTEGER          CHECK (budget_min >= 0),
    budget_max        INTEGER,
    score             DOUBLE PRECISION NOT NULL DEFAULT 0,
    score_updated_at  TIMESTAMPTZ,
    hidden            BOOLEAN          NOT NULL DEFAULT false,
    created_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CHECK (budget_max >= budget_min)
);
CREATE INDEX course_region_idx   ON course (sido, sigungu, score DESC) WHERE NOT hidden;
CREATE INDEX course_location_idx ON course (center_lat, center_lng) WHERE NOT hidden;
CREATE UNIQUE INDEX course_auto_anchor_idx ON course (anchor_place_id) WHERE kind = 'auto';
COMMENT ON TABLE  course                  IS '데이트 코스. 관리자가 등록하거나(curated) 중심 장소로 자동 조합한다(auto)';
COMMENT ON COLUMN course.anchor_place_id  IS '자동 코스의 중심 장소. 중심 장소마다 자동 코스는 하나';
COMMENT ON COLUMN course.center_lat       IS '코스 단계 좌표의 가운데 (지도·주변 찾기용)';
COMMENT ON COLUMN course.total_minutes    IS '걸리는 시간(분). 머무는 시간 + 걸어서 옮기는 시간';
COMMENT ON COLUMN course.budget_min       IS '예상 예산 하한(원, 2인). 큐레이션 코스만';
COMMENT ON COLUMN course.budget_max       IS '예상 예산 상한(원, 2인)';
COMMENT ON COLUMN course.score            IS '순위 점수 캐시 (키워드 일치는 검색 때 더한다). 반응이 쌓이면 다시 계산';
COMMENT ON COLUMN course.hidden           IS '관리자가 숨긴 코스 (목록·검색에서 뺀다)';

CREATE TABLE course_step (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    course_id         BIGINT       NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    step_order        SMALLINT     NOT NULL CHECK (step_order >= 1),
    place_id          BIGINT       NOT NULL REFERENCES place (id),
    role              VARCHAR(10)  NOT NULL CHECK (role IN ('spot', 'cafe', 'meal')),
    stay_minutes      INTEGER      CHECK (stay_minutes > 0),
    walk_minutes      INTEGER      CHECK (walk_minutes >= 0),
    memo              VARCHAR(200),
    UNIQUE (course_id, step_order)
);
CREATE INDEX course_step_place_idx ON course_step (place_id);
COMMENT ON TABLE  course_step              IS '코스의 단계 (순서대로 방문하는 장소)';
COMMENT ON COLUMN course_step.role         IS 'spot(구경·놀거리), cafe(카페), meal(식사). 근처 대안을 찾을 때 같은 역할로 찾는다';
COMMENT ON COLUMN course_step.stay_minutes IS '머무는 시간(분)';
COMMENT ON COLUMN course_step.walk_minutes IS '앞 단계에서 걸어서 오는 시간(분). 첫 단계는 NULL. 처음에는 직선 거리로 어림한다';
COMMENT ON COLUMN course_step.memo         IS '관리자 한 줄 설명 (예: 창가 자리 추천)';

CREATE TABLE course_keyword (
    course_id   BIGINT NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    keyword_id  BIGINT NOT NULL REFERENCES keyword (id) ON DELETE CASCADE,
    PRIMARY KEY (course_id, keyword_id)
);
CREATE INDEX course_keyword_keyword_idx ON course_keyword (keyword_id);
COMMENT ON TABLE course_keyword IS '코스 키워드. 자동 코스는 단계 장소 키워드에서 모은다';

-- 4) 반응 (로그인 없음)
CREATE TABLE reaction (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    course_id   BIGINT      NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    visitor_id  UUID        NOT NULL,
    kind        VARCHAR(10) NOT NULL CHECK (kind IN ('save', 'like', 'visited')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (course_id, visitor_id, kind)
);
CREATE INDEX reaction_score_idx   ON reaction (course_id, kind, created_at);
CREATE INDEX reaction_visitor_idx ON reaction (visitor_id, kind, created_at DESC);
COMMENT ON TABLE  reaction            IS '코스 반응. 한 방문자는 코스마다 종류별로 한 번만';
COMMENT ON COLUMN reaction.visitor_id IS '방문자 id (브라우저마다 만드는 UUID). 사람을 알아볼 수 있는 정보는 두지 않는다';
COMMENT ON COLUMN reaction.kind       IS 'save(저장), like(좋아요), visited(다녀왔어요). 순위 점수는 visited > save > like';
