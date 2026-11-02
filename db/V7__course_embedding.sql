-- AI 추천(실험 기능)의 코스 임베딩: 코스를 글로 적어(지역·단계 장소·업종·키워드) Ollama embeddinggemma 로 바꾼 벡터.
--  요청 문장과 비슷한 코스를 후보에 넣을 때 쓴다. 코스 1만여 개라 pgvector 없이 서버 메모리에서 비교한다.
--  만들기는 수집기 명령(--collect=embeddings)으로, 글(text_hash)이 바뀐 코스만 다시 만든다.

SET search_path TO date_course;

CREATE TABLE course_embedding (
    course_id   BIGINT       PRIMARY KEY REFERENCES course (id) ON DELETE CASCADE,
    model       VARCHAR(60)  NOT NULL,
    text_hash   CHAR(16)     NOT NULL,
    vector      REAL[]       NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  course_embedding           IS 'AI 추천용 코스 임베딩 (Ollama). 코스를 다시 만들면 글이 바뀐 것만 다시 만든다';
COMMENT ON COLUMN course_embedding.text_hash IS '임베딩한 글의 SHA-256 앞 16자 (같으면 다시 만들지 않음)';
COMMENT ON COLUMN course_embedding.vector    IS '정규화한 벡터 (embeddinggemma 768차원). 코사인 = 내적';
