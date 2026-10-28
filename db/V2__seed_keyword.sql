-- 키워드 첫 목록 (기획안 "키워드 체계")
-- 예산은 2인 기준. 금액 구간은 초안이라 바뀌면 새 마이그레이션으로 이름만 고친다 (code 는 그대로)

SET search_path TO date_course;

INSERT INTO keyword (code, name, kind, sort_order) VALUES
    ('quiet',       '조용',        'mood',     10),
    ('romantic',    '로맨틱',      'mood',     20),
    ('lively',      '활기찬',      'mood',     30),
    ('cozy',        '감성',        'mood',     40),
    ('unique',      '이색적',      'mood',     50),
    ('walk',        '산책',        'activity', 10),
    ('exhibition',  '전시·문화',   'activity', 20),
    ('workshop',    '체험·공방',   'activity', 30),
    ('shopping',    '쇼핑',        'activity', 40),
    ('play',        '액티비티',    'activity', 50),
    ('night-view',  '야경',        'activity', 60),
    ('food-tour',   '맛집 투어',   'activity', 70),
    ('indoor',      '실내',        'setting',  10),
    ('outdoor',     '야외',        'setting',  20),
    ('budget-low',  '3만원 이하',  'budget',   10),
    ('budget-mid',  '3~7만원',     'budget',   20),
    ('budget-high', '7만원 이상',  'budget',   30);
