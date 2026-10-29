# AGENTS.md: AI 작업 가이드

이 문서는 AI 코딩 도구(Claude Code, ChatGPT/Codex, Cursor 등)가 이 저장소에서 작업할 때 따라야 할 규칙과 맥락입니다.
사람이 읽어도 프로젝트 전체를 파악할 수 있게 작성합니다. 기획은 `docs/기획안.md`, 데이터·코스·순위·검색의 실제 규칙은 `docs/동작방식.md` 에 있습니다. 규칙을 바꾸면 그 문서와 `README.md` 도 함께 고칩니다.

## 1. 프로젝트 한 줄 요약

**데이트코스 추천**: 지도에서 지역과 키워드(분위기·활동·실내외·예산)를 고르면, 순위가 매겨진 데이트 코스와 근처 식당·카페를 보여주는 웹 서비스입니다. 주소 `https://date-course.kimseongmin.co.kr`.

- **로그인은 없습니다.** 저장·좋아요·다녀왔어요는 브라우저마다 만드는 방문자 id 로 구분합니다. 로그인은 관리자 화면에만 둡니다.
- 장소는 **공공데이터 상가(상권)정보와 한국관광공사 TourAPI** 두 곳에서 모아 중복을 합칩니다. 지도 서비스의 리뷰·별점을 긁어 오지 않습니다(약관 위반).
- **무료로만 운영합니다.** 그래서 쓰지 않는 것:
  - 카카오 API: 2026-07-21 부터 카카오맵 API 무료 쿼터는 계정의 첫 번째 앱에만 주고, 그 밖에는 결제 수단(비즈월렛)을 연결해야 합니다.
  - 네이버 검색 API: 2026-07 말 개발자센터 신규 신청이 닫혔고, 옮겨 간 NAVER API HUB 는 유료화가 예고됐습니다. 네이버지도 링크는 API 없이 검색 주소(`https://map.naver.com/p/search/{이름}`)로 겁니다.
- 네이버 클라우드 Maps 는 **Dynamic Map**(화면 지도, 대표 계정 1개에 월 6,000,000건 무료)과 **Geocoding**(주소 검색, 월 3,000,000건 무료)만 무료 이용량 안에서 씁니다. 넘으면 바로 과금되므로 콘솔의 이용 한도를 그 이하로 걸어 둡니다(6절).
- 순위는 **큐레이션(관리자 등록 코스) + 사용자 반응**에 키워드 일치·동선·출처 일치를 더한 점수입니다. 범위는 전국이고, 큐레이션이 없는 지역은 코스를 자동으로 조합합니다.

## 2. 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 백엔드 | Java 17, Spring Boot 4.1, Gradle(wrapper), Spring Web MVC, Spring Data JPA |
| DB | PostgreSQL 16. 힐링룸과 **같은 DB(`healing_room`)·계정**, 테이블은 전용 스키마 **`date_course`** 에만 (`currentSchema`, `hibernate.default_schema`) |
| 캐시 | Redis 7. 힐링룸과 같은 계정, 키 접두어 `healing-room:date-course:` (5-1절) |
| 프론트엔드 | React 19, TypeScript, Vite, react-router. 지도는 네이버 지도 JavaScript API v3 (Dynamic Map) |
| 테스트 | JUnit 5, MockMvc, Mockito (`@WebMvcTest`, `@MockitoBean`) |

## 3. 폴더 구조

```
/
├─ AGENTS.md / CLAUDE.md / README.md / .editorconfig / .gitattributes (모든 글 파일 UTF-8, 줄바꿈 LF)
├─ docs/기획안.md                  ← 기획 (무엇을 왜, 화면, 정한 것·남은 일)
├─ docs/동작방식.md                ← 수집 → 키워드 → 코스 조합 → 순위 → 검색의 실제 규칙과 숫자
├─ deploy/                         ← 서버 설치·배포 (systemd, nginx, ci-receive, install-root). 9절
├─ db/                             ← DB 스키마 SQL (수동 실행, 5절)
├─ backend/                        ← Spring Boot 앱 (Gradle 프로젝트 루트)
│  ├─ .env.example                 ← 환경변수 형식 (.env 는 git 제외)
│  └─ src/main/java/com/datecourse/
│     ├─ DateCourseApplication.java
│     ├─ domain/{이름}/            ← 도메인마다 controller / service / repository / entity / dto (관리자 admin 은 예정)
│     │  ├─ place/                 Place, PlaceSource(출처별 원본), PlaceMergeCandidate(중복 후보), PlaceKeyword
│     │  │                         enum: PlaceCategory, PlaceSourceType(sbiz/tour), MergeStatus, KeywordOrigin
│     │  ├─ place/ (검색)          LocationSearchController GET /api/locations, LocationSearchService(하루 캐시·Geocoding 한도),
│     │  │                         LocationSearchRepository(시군구·동, 명소), client/NaverGeocodeClient
│     │  ├─ keyword/               Keyword, KeywordKind(mood/activity/setting/budget), KeywordController GET /api/keywords (Redis 캐시)
│     │  ├─ course/                Course(단계·키워드 포함), CourseStep, CourseKind(curated/auto), StepRole(spot/cafe/meal),
│     │  │                         AutoCourseService(자동 코스 다시 만들기), util/CoursePlanner(조합 규칙)·PlaceGrid(반경 찾기),
│     │  │                         CourseScoreService(순위 점수, 매일 04:30), CourseQueryService·CourseQueryRepository(검색·상세),
│     │  │                         CourseController GET /api/courses, GET /api/courses/{id} (7절 "API")
│     │  ├─ reaction/              Reaction, ReactionKind(save/like/visited)
│     │  └─ collector/             장소 수집 (6절 "장소 수집기")
│     │     ├─ CollectorRunner     --collect=… 로 실행하면 웹 서버 없이 수집하고 종료
│     │     ├─ client/             TourApiClient (areaBasedList2, 1,000개씩)
│     │     ├─ service/            PlaceUpsertService(한 곳씩 넣기, TourAPI), TourCollector, TourItemMapper(관광타입 → 우리 분류),
│     │     │                      SbizImporter(상가정보 CSV 를 묶어서 넣기), SbizRowMapper(업종 → 우리 분류),
│     │     │                      PlaceKeywordTagger(장소 키워드 자동으로 다시 붙이기)
│     │     ├─ dto/                PlaceCandidate (출처 하나에서 읽은 장소)
│     │     └─ util/               PlaceMatcher(같은 장소 규칙), PlaceNames(정리한 이름·지점명), Geo(거리·사각형),
│     │                            Regions(주소 → 시도·시군구), CsvLine(따옴표를 아는 CSV 한 줄 나누기),
│     │                            PlaceKeywordRules(분류·업종·이름 → 키워드)
│     └─ global/
│        ├─ controller/            HealthController  GET /api/health
│        ├─ exception/             ApiException(공통 부모), GlobalExceptionHandler (→ { "message": ... })
│        ├─ cache/                 JsonCache (Redis 캐시, 5-1절)
│        └─ util/                  LowercaseEnumConverter (enum 을 DB 에 소문자로: 각 enum 의 DbConverter 가 상속)
└─ frontend/                       ← Vite + React 앱
   ├─ vite.config.ts               /api 를 백엔드(18290)로 넘김
   └─ src/
      ├─ App.tsx                   화면 주소(라우트), 전체를 MapLayout 으로 감쌈
      ├─ map/                      MapLayout(왼쪽 화면 + 오른쪽 지도, 휴대폰은 지도가 위), context.ts(useMap: 지도·지도 위 버튼 자리)
      ├─ components/               KeywordPicker (키워드 칩: 3개까지, 실내외·예산은 하나만), LocationSearch (지역 검색창)
      ├─ types/                    naver-maps.d.ts (네이버 지도 v3 중 쓰는 것만 적은 타입)
      ├─ lib/                      api.ts(fetch 래퍼, 오류는 서버 message 그대로), types.ts(API 응답 모양), naverMap.ts(스크립트 한 번만 불러오기, 숫자 핀, 반경↔확대),
      │                            keywords.ts(키워드 목록 한 번만), areas.ts(인기 지역 바로가기), format.ts(시간·거리·이름표)
      └─ pages/                    HomePage(/), SearchPage(/search?lat=&lng=&r=&kw=), CoursePage(/courses/:id), SavedPage(/saved), NotFoundPage
```

- 다른 도메인의 entity 를 컨트롤러에서 직접 쓰지 말고 service 를 통해 접근합니다.
- 테스트도 같은 패키지 구조를 따릅니다.

## 4. 실행 · 테스트 (로컬)

```bash
cd backend
./gradlew bootRun     # → http://localhost:18290 (Windows: gradlew.bat bootRun)
./gradlew test        # 실제 DB 에 붙는 통합 테스트(@Tag("integration"))까지. CI 는 ./gradlew test -Pci (DB 없이)

cd frontend
npm install
npm run dev           # → http://localhost:5173 (/api 는 백엔드로)
npm run build         # 타입 검사 + 빌드 → dist/
npm run lint
```

- `.env` 는 `backend/.env` 에 둡니다. `spring.config.import=optional:file:.env[.properties]` 로 읽으므로 **작업 디렉터리가 `backend/` 이어야** 합니다.
- 포트는 환경변수 `PORT` 가 있으면 그 값, 없으면 **18290** 입니다(같은 서버의 힐링룸이 18280).

## 5. ⚠️ DB 규칙

0. **힐링룸과 DB·계정을 함께 씁니다.** 이 앱의 테이블은 모두 `date_course` 스키마에 두고, `public`(힐링룸)은 절대 건드리지 않습니다. 마이그레이션 파일은 `SET search_path TO date_course;` 로 시작하고, 앱은 `DB_SCHEMA`(`currentSchema`)로 이 스키마만 봅니다.
1. **애플리케이션이 DB 스키마를 만들거나 바꾸면 안 됩니다.** `ddl-auto=validate`, `spring.sql.init.mode=never` 를 유지하고, Flyway·Liquibase 같은 자동 마이그레이션 도구를 넣지 않습니다.
2. 스키마 변경은 `db/V{번호}__{설명}.sql` 을 **새 파일로 추가**하고 사람이 직접 실행합니다. 이미 실행한 파일은 고치지 않습니다.
3. 실행할 때는 `SET lock_timeout = '5s'` 를 먼저 겁니다.
4. 엔티티를 바꾸는 배포는 마이그레이션을 운영 DB 에 먼저 실행한 뒤 푸시합니다.
5. 엔티티를 API 로 그대로 내보내지 말고 `record` DTO 로 바꿉니다. `open-in-view=false`.
6. 운영 DB 는 `ssh ai-server` 에서 힐링룸의 `~/healing-room/shared/.env` 를 읽어 `psql` 로 다룹니다. 그 파일에는 Spring 설정 줄도 있어 `KEY=값` 줄만 읽습니다(`eval "$(grep -E '^[A-Z_][A-Z0-9_]*=' …)"`). 값은 출력하지 않습니다.

### 마이그레이션 현황
| 파일 | 내용 | 적용 |
|---|---|---|
| `V1__init.sql` | `date_course` 스키마, place·place_source·place_merge_candidate·keyword·place_keyword·course·course_step·course_keyword·reaction | ✅ |
| `V2__seed_keyword.sql` | 키워드 17개 (분위기 5, 활동 7, 실내외 2, 예산 3) | ✅ |
| `V3__place_badge.sql` | `place_badge` (공공기관 지정 표시: 모범음식점). 엔티티 없이 JDBC 로만 씀 | ✅ |

## 5-1. 캐시는 Redis

- **캐시처럼 잠깐 들고 있다가 다시 쓰는 값(외부 API 응답, 자주 읽는 목록·순위 등)은 서버 메모리가 아니라 Redis 에 둡니다.**
- 힐링룸과 **같은 Redis·계정**(`REDIS_*` 는 힐링룸 .env 와 같은 값, DB 1번)을 씁니다. 이 계정은 `healing-room:` 으로 시작하는 키만 쓸 수 있어서(ACL `~healing-room:*`) 이 앱의 키는 모두 **`healing-room:date-course:`** 로 시작합니다(`REDIS_KEY_PREFIX`). 힐링룸 키(`healing-room:chat:` 등)는 건드리지 않습니다.
- 캐시는 `global/cache/JsonCache.getOrLoad(key, ttl, type, loader)` 로: 값은 JSON 문자열, **모든 캐시에 TTL**. 원본이 바뀌면 `evict(key)`.
- `KEYS`·`FLUSHDB` 같은 전체 명령은 쓰지 않습니다(계정에서도 막혀 있음). 키를 찾을 땐 `SCAN` + `healing-room:date-course:*`.
- Redis 가 안 돼도 서비스는 돌아갑니다: `JsonCache` 가 읽기·쓰기 실패를 로그만 남기고 loader 를 그대로 부릅니다.

| 키 | 값 | 수명 |
|---|---|---|
| `healing-room:date-course:keywords` | 키워드 목록 (`GET /api/keywords`) | 24시간 |
| `healing-room:date-course:courses:search:{위도}:{경도}:{반경}:{키워드}:{쪽}` | 코스 검색 결과 (좌표는 소수 셋째 자리 ≈100m, 키워드 정렬) | 10분 |
| `healing-room:date-course:courses:{id}` | 코스 상세 | 1시간 |
| `healing-room:date-course:franchise-names` | 체인 판별 목록 (흔한 이름·브랜드, `FranchiseNames`) | 1일 (자동 코스를 다시 만들 때 새로) |
| `healing-room:date-course:places:nearby:{위도}:{경도}:{검색어}` | 코스에 넣을 장소 찾기 결과 | 10분 |
| `healing-room:date-course:location:{검색어}` | 지역 검색 결과 (지역·명소·주소) | 1일 (못 찾았으면 10분: 키를 막 넣었거나 잠깐 오류여도 하루 내내 빈 결과가 나가지 않게) |
| `healing-room:date-course:geocode-count:{날짜}` | 그날(한국 시간) Geocoding 부른 수 (하루 한도 확인) | 2일 |

## 6. 외부 API 규칙

- 키는 `backend/.env` 에만 둡니다(`.env.example` 참고). **공개 저장소이므로 키·비밀번호를 코드·문서·커밋 메시지에 쓰지 않습니다.**
- TourAPI 는 **서버(수집기)만** 부릅니다. 상가(상권)정보는 공공데이터포털에서 분기마다 CSV 파일을 받아 넣습니다(키 없음, 이용 제한 없음).
- 화면은 우리 API 와 네이버 지도만 씁니다. 지도 키(`frontend/.env` 의 `VITE_NAVER_MAP_KEY_ID`, 스크립트 `https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=…`)는 브라우저에 들어가므로 콘솔에 Web 서비스 URL 을 등록해 도메인을 제한합니다.
- 네이버 클라우드 Maps 는 **Dynamic Map**(화면 지도, 월 600만 건 무료)과 **Geocoding**(주소 검색, 월 300만 건 무료, 서버에서만)만 신청합니다. Static Map, Directions(길찾기)는 쓰지 않습니다. Geocoding 은 Client Secret 이 필요해서 `NaverGeocodeClient` 가 서버에서만 부르고(`NAVER_MAP_CLIENT_ID`·`NAVER_MAP_CLIENT_SECRET`, 화면에 넣지 않음), 사이트 전체 하루 3,000번(`NAVER_GEOCODE_DAILY_LIMIT`)·검색어마다 하루 캐시로 무료 이용량을 넘지 않게 합니다. 지도는 화면을 열 때 한 번만 만들고, 페이지를 옮길 때 다시 만들지 않게 합니다(호출 수 아끼기).
- 결과를 얼마나 오래 저장해도 되는지는 각 서비스 약관을 따릅니다. 리뷰·별점 수집 금지.

### 장소 수집기 (collector)
```bash
cd backend
./gradlew bootRun --args="--collect=tour"            # TourAPI 전국 (관광지 12, 문화시설 14, 레포츠 28, 음식점 39)
./gradlew bootRun --args="--collect=tour --area=1"   # 서울만 (TourAPI areaCode)
./gradlew bootRun --args="--collect=sbiz --path=../data/sbiz/202606"              # 상가정보 CSV 폴더 전부 (시도별 16개)
./gradlew bootRun --args="--collect=sbiz --path=../data/sbiz/202606 --only=서울"  # 파일 이름에 '서울'이 든 것만
./gradlew bootRun --args="--collect=keywords"        # 장소 키워드 다시 붙이기 + 자동 코스 다시 만들기 (규칙을 고친 뒤)
./gradlew bootRun --args="--collect=courses"         # 자동 코스 다시 만들기 + 순위 점수
./gradlew bootRun --args="--collect=scores"          # 순위 점수만 (매일 04:30 에도 저절로 돔)
./gradlew bootRun --args="--collect=model --path=../data/model"  # 모범음식점 CSV → place_badge, 그 뒤 코스·점수 다시
# tour·sbiz 수집이 끝나면 키워드 → 자동 코스 → 순위 점수까지 저절로 돈다
```
- 웹 서버 없이 뜨고(`DateCourseApplication` 이 `--collect` 를 보면 끔) 끝나면 종료합니다. 돌고 있는 서버와 포트가 겹치지 않습니다. devtools 재시작도 꺼서, 수집 중에 다른 곳에서 빌드해도 수집이 처음부터 다시 시작되지 않습니다.
- **중복 규칙**(`PlaceMatcher`, 두 수집기가 같이 씀): 같은 출처·원본 id 는 마지막으로 본 시각만 고침 → 정리한 이름(`PlaceNames.normalize`)이 같고 **50m 안**이면 같은 장소에 출처만 더함 → **50~150m** 면 새 장소 + `place_merge_candidate`(관리자가 정함). 둘 다 지점명이 있는데 다르면(강남점 ↔ 역삼점) 다른 가게. **관광명소는 관광명소끼리만** 합칩니다(관광지 "인사동" 옆 가게 "인사동"이 합쳐진 적이 있음).
- **넣는 방식**: TourAPI(수천 곳)는 `PlaceUpsertService` 로 한 곳씩. 상가정보(시도 하나에 십만 단위)는 한 곳씩 DB 에 물으면 몇 시간이 걸려서, `SbizImporter` 가 시도의 기존 장소를 메모리에 올려 비교하고 5,000곳씩 트랜잭션·JDBC batch(`reWriteBatchedInserts`)로 넣습니다. 새 장소 id 는 시퀀스에서 1,000개씩 받아(`OVERRIDING SYSTEM VALUE`) 출처·중복 후보가 바로 가리키게 합니다.
- **상가정보 업종 → 우리 분류**(`SbizRowMapper`): 카페(카페·빵/도넛·아이스크림/빙수), 음식점(한식·일식·중식·서양식·동남아·기타 외국 전부, 분식·치킨·피자·버거·토스트·뷔페), 술집(요리 주점·생맥주 전문, 유흥 주점 제외), 소품샵 등(기념품점·예술품·꽃집·액세서리/잡화·문구·서점·장난감·음반), 놀거리(노래방·오락장·게임장·볼링장·당구장·기타 오락 서비스·수상 레저). 나머지 업종(병원·편의점·PC방·독서실 등)은 넣지 않습니다. 열은 이름으로 찾습니다(분기마다 순서가 바뀌어도 되게).
- 다른 출처에서 같은 장소를 보면 비어 있는 칸(주소·전화·사진·소개)만 채웁니다.
- TourAPI 음식점(39) 중 분류 `FD05`(카페·전통찻집)와 cat3 `A05020900` 은 카페, 나머지는 음식점. TourAPI 는 시도·시군구 칸이 없어 주소 앞부분에서 꺼냅니다(`Regions`).
- **시도 이름은 상가정보의 공식 이름 16개로 맞춥니다**(`Regions.canonicalSido`). TourAPI 주소는 "경남", "서울", "광주광역사" 처럼 줄이거나 틀리게 쓴 게 섞여 있어(전국 156곳) 지역 검색에서 빠졌습니다. 시도를 알 수 없는 주소("김제시 …", "닫기 …")는 건너뜁니다. 2026 년부터 전남·광주는 `전남광주통합특별시`.
- **장소 키워드 자동으로 붙이기**(`PlaceKeywordRules`, `PlaceKeywordTagger`): 확실한 것만 붙입니다(모르면 안 붙임).
  - 업종 규칙: `detail_category` 가 접두어로 시작하면. 상가정보는 업종 이름(노래방 → 액티비티·활기찬), TourAPI 는 분류 코드 `lclsSystm3`(`NA` 자연 → 산책·야외, `VE070600` 미술관 → 전시·조용·감성). 코드 뜻은 TourAPI `lclsSystmCode2` 로 봅니다.
  - 이름 규칙: 정리한 이름에 루프탑·와인·공방·방탈출·북카페·갤러리 같은 말이 있으면.
  - 실내외·예산은 **하나만**: 짧은 접두어 → 긴 접두어 → 이름 순으로 덮어씁니다. 실내외가 없으면 관광명소는 야외, 나머지는 실내. 예산은 음식점·카페·술집에만(값싼 업종 3만원 이하, 비싼 업종 7만원 이상, 나머지 음식점·술집 중간, 카페 낮음). 음식점은 모두 "맛집 투어".
  - id 2만 개씩 한 트랜잭션으로 그 구간의 `auto` 를 지우고 다시 넣습니다. `admin`(관리자가 붙임)은 두고, 관리자가 실내외·예산을 정한 장소에는 그 종류의 `auto` 를 넣지 않습니다. 몇 번 돌려도 결과가 같습니다.
- **자동 코스**(`CoursePlanner`, `AutoCourseService`): 중심 장소 → (구경할 곳 하나 더) → 카페 → 식당, 3~4단계.
  - 중심 장소: 관광명소·문화시설 (교통시설·학교·컨벤션·어린이공원·스포츠센터·카지노 등 분류와, 이름에 연도가 든 지난 행사는 뺌).
  - 구경할 곳: 소품샵 등(꽃집·문구점 제외, "서점" 업종은 학습지·교육 업체가 섞여 있어 이름에 책방·서점·문고·북이 든 곳만), 다른 명소·문화시설, 볼링장·게임장·오락장·수상 레저, 이름으로 "이색적"이 붙은 놀거리(방탈출 등). 없으면 3단계.
  - 거리: 단계 사이 직선 700m, 모든 단계가 중심 장소에서 1km 안. 걷는 시간 = 직선 × 1.3 ÷ 분속 67m. 머무는 시간: 명소·문화 60분(두 번째면 50), 놀거리 90/60, 소품샵 30, 카페 60, 식당 70.
  - 후보 점수: 중심 장소와 분위기 키워드가 겹칠 때마다 +2, TourAPI 에도 있으면 +2, 체인점(`FranchiseNames`)  −3, 250m 마다 −1, 업종 점수(파스타/스테이크 +3 … 국밥·분식 −2), 중심 장소와 같은 분류의 구경거리(공원 → 공원) −1, 이미 다른 코스에 넣은 횟수마다 −0.7(명소가 몰린 인사동에서 한 찻집이 44개 코스에 들어간 적이 있음). 카페나 식당이 없으면 코스를 만들지 않습니다.
  - 코스 키워드: 구경거리 단계의 분위기·활동 + 카페·식당의 분위기, 실내외는 중심 장소, 예산은 식당, "맛집 투어"는 식당이 TourAPI 에 있을 때만.
  - **목적지 카페 코스**: 관광공사가 소개하고 체인이 아닌 카페(`isDestinationCafe`) 중 명소 코스에 안 들어간 곳은 그 카페를 중심으로 (근처 구경거리) → 카페 → 식당(`planAroundCafe`). 소개 카페의 절반(1,492곳)이 어떤 코스에도 없었음. 2026-10-29 1,406개.
  - **체인 판별**(`place/service/FranchiseNames`, 코스 만들기·코스 상세 고른 이유·근처 대안이 같이 씀): ① 같은 이름이 전국 15곳 넘게 ② 브랜드(흔한 이름 중 30% 이상에 지점명, 3자 이상, 946개)로 시작하고 뒤에 6자 이내("투썸플레이스서초"). 집계가 30초 넘게 걸려 Redis `franchise-names` 에 하루 두고, 웹 서버는 켜질 때 뒤에서 읽으며 그 전에는 "체인 아님"으로 봄(화면이 멈추지 않게). 자동 코스를 만들 때는 `reload()` 로 다시 집계.
  - 이미 고른 곳과 같은 장소는 다시 고르지 않음(`alreadyTaken`): 이름이 같거나, 50m 안에서 한 이름이 다른 이름을 품으면(출처마다 이름이 달라 따로 들어간 같은 가게).
  - **동네 코스**: 명소 코스가 1.5km 안에 하나도 없는 동네(주거지·역세권, 예: 대전 반석역)는 구경거리(소품샵·서점·볼링장·게임장·이름으로 "이색적"인 놀거리)를 중심으로 같은 규칙으로 만듭니다. 약 1km 칸마다 가장 나은 구경거리 하나(`anchorRank`: 분위기 키워드 수, TourAPI +2, 체인점 −3)로 해 보고, 만든 코스도 다음 칸의 "1.5km 안에 있음"에 셉니다. 2026-10-29 805개 (명소 9,602 + 목적지 카페 1,406 + 동네 805 = 11,813).
  - 중심 장소마다 하나(`course_auto_anchor_idx`): 다시 만들 때 같은 id 를 고쳐 써서 반응이 이어집니다. 이번에 못 만든 예전 자동 코스는 반응이 없으면 지우고, 있으면 숨깁니다. 시도마다 장소를 메모리에 올려 조합하므로 시도 경계 너머의 카페·식당은 보지 않습니다.
- **모범음식점**(`ModelRestaurantImporter`): 행정안전부 전국모범음식점표준데이터 CSV(공공데이터포털 15096282, CP949, 칸 안 줄바꿈 있음)를 `data/model/` 에 받아 넣습니다. 좌표가 없어 주소(`AddressKey`: 시도·층·괄호를 뗀 "시군구 길 번호")와 이름(같거나 한쪽이 품음)으로 식당·카페·술집에 맞춥니다. 영업 중·지정 취소 없음만. 넣을 때마다 `model_restaurant` 표시를 모두 지우고 다시 넣습니다. 2026-10-29: 11,308곳 중 9,616곳(85%). 자동 다운로드는 403 이라 사람이 받습니다.
- 상가(상권)정보 CSV 는 공공데이터포털(15083033)에서 분기마다 zip 으로 받아 `data/sbiz/{기준월}/`(git 제외, 전국 1.5GB)에 풉니다. 파일은 시도별이고 UTF-8 입니다.

## 7. API

오류는 `{ "message": "화면에 그대로 보여줄 문장" }` 입니다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/health` | `{ status: "ok" }` |
| GET | `/api/keywords` | 키워드 칩 `[{ code, name, kind }]` (분위기 → 활동 → 실내외 → 예산) |
| GET | `/api/courses?lat=&lng=&radius=&kw=&page=` | 코스 순위 목록. 지도 가운데에서 `radius`(m, 기본 3000, 500~20000) 안의 코스 가운데, `kw` 0~3개(`kw=quiet,cozy` 또는 `kw=quiet&kw=cozy`), `page` 0~9(한 쪽 20개) → `{ total, page, size, courses: [{ id, title, summary, kind, sido, sigungu, centerLat, centerLng, totalMinutes, distanceMeters, keywords, matchedKeywords, steps: [{ order, role, stayMinutes, walkMinutes, placeId, name, branch, category, lat, lng }] }] }`. 국외 좌표·반경·모르는 키워드·4개 이상은 400 |
| GET | `/api/locations?q=` | 지역 검색(2~40자) → 최대 10개 `[{ kind, name, detail, lat, lng, radius }]`. `station` 역(지하철역 좌표가 데이터에 없어서 이름·지점명에 "반석역"이 든 가게 2곳 이상의 평균 좌표, 시군구마다 하나, 실제 역과 약 100m), `area` 시군구·행정동(우리 장소들의 평균 좌표, "성수동" → 끝의 동을 떼고 성수1가1동 등)과 법정동(지번 주소의 동: "반석동"은 행정동으로 노은3동이라), `place` 명소·문화시설(이름에 검색어), `address` 네이버 Geocoding(키가 있고 하루 한도 안일 때. 역·가게 이름은 못 찾음). "대전 반석역"처럼 앞에 시도를 쓰면 그 시도 안에서만(같은 이름이 여러 도시에 있어서). `radius` 는 지도에 보여줄 반경 제안 |
| GET | `/api/courses/{id}/steps/{order}/alternatives` | 그 단계의 근처 대안(같은 역할: 구경·카페·식당, 원래 장소에서 600m 안) 좋은 순 5곳 `[{ place, reasons, distanceMeters }]`. 코스 만들 때와 같은 점수(`CoursePlanner.alternativeScore`), 코스에 든 곳·같은 가게는 뺌. 1시간 캐시(`courses:{id}:alt:{order}`). 없는 코스·단계 404 |
| GET | `/api/places/nearby?lat=&lng=&q=` | 코스에 넣을 장소 찾기: (lat, lng) 에서 2km 안, 이름이나 업종에 q 가 든 곳 10곳 `[{ place, reasons, distanceMeters }]`. 순서: 이름이 같음 → 이름이 q 로 시작 → 업종이 q("서점") → 이름에 들어 있기만("세무서점"), 그 안에서 가까운 순. 10분 캐시. q 없음·국외 좌표 400 |
| GET | `/api/courses/{id}` | 코스 상세: 위 + `budgetMin`·`budgetMax`, 단계마다 `memo` 와 장소(`roadAddress`, `lotAddress`, `phone`, `imageUrl`, `description`, `naverMapUrl`). 없거나 숨긴 코스 404 |

### 순위
- 검색 순위 = `course.score` + 키워드 일치 40 × (맞은 수 ÷ 고른 수) + 가까움 10 × (1 − 거리 ÷ 반경). 키워드는 거르지 않고 점수로만 씁니다(맞는 코스가 적은 동네에서도 목록이 비지 않게).
- 고른 반경 안에 코스가 하나도 없으면 첫 쪽에서 반경을 두 배씩 10km 까지 넓혀 찾고 `radius`(실제 반경)·`widened: true` 를 돌려줍니다. 화면은 주소의 `r` 을 넓힌 반경으로 바꾸고 "넓혀 가까운 코스를 찾았어요"라고 알립니다.
- `course.score`(`CourseScoreService`, 매일 04:30 과 자동 코스를 다시 만든 뒤): 큐레이션 +20, 반응 10 × ln(1 + 점수)(다녀왔어요 5·저장 3·좋아요 1, 90일 지난 반응 0.3배), 동선 10 × (1 − 평균 걷는 시간 ÷ 15분), 출처 일치 10 × (상가정보·TourAPI 에 모두 있는 단계 비율), 4단계 +2. 2026-10-29 자동 코스 점수는 1.3~17.3(평균 9.6).
- 네이버지도 링크는 API 없이 `https://map.naver.com/p/search/{이름 지점 시군구}`.
- 서버(로컬 포함)는 모두 같은 운영 DB 를 보므로 로컬 서버의 새벽 점수 계산도 운영 DB 를 고칩니다(같은 결과라 문제는 없음).

### 화면 (frontend)
- **지도는 앱에 하나**: `MapLayout` 이 처음 한 번만 `new naver.maps.Map` 을 만들고, 화면들은 `useMap()` 으로 받아 핀·선만 그렸다 지웁니다(useEffect 정리 함수에서 `setMap(null)`). Dynamic Map 은 지도를 만들 때마다 이용 횟수가 세어지기 때문입니다.
- 지도를 옮길 때는 `morph`(애니메이션) 대신 `jumpTo`(`setZoom` + `setCenter`)를 씁니다. 탭이 뒤에 있으면 morph 가 확대를 덜 한 채 멈춥니다.
- 네이버 지도는 지도 칸에 `position: relative` 를 넣으므로 `.map` 은 `inset` 대신 `width/height: 100%` 로 채웁니다(처음에 높이 0 이 돼 지도가 안 보였음).
- 지역은 검색창(`LocationSearch` → `GET /api/locations`, 엔터·찾기를 누를 때만 부름: **첫 결과로 바로 지도를 옮기고 파란 핀**, 나머지는 "다른 곳이었나요?"에서 바꿔 고름), 지도 옮기기, 인기 지역 바로가기(`lib/areas.ts`), 내 주변(브라우저 위치)으로 고릅니다. 검색 화면에서 다른 지역을 고르면 바로 그곳에서 다시 찾습니다. 검색 반경은 지금 지도 화면을 덮는 반경(`visibleRadius`, 500m~20km)입니다.
- 검색 화면: 순위 핀(코스 가운데) → 카드나 핀을 누르면 그 코스의 단계 핀과 동선(점선)을 그리고 지도를 맞춥니다.
- **핀은 모양부터 다르게**(`lib/naverMap.ts`): 순위는 분홍 물방울 마커+숫자(`rankIcon`), 코스를 고른 동안 다른 코스는 작고 연한 물방울+숫자(`rankIcon(n, true)`, 눌러서 바로 바꿈), 단계는 어두운 이름표 "① 카페"(`stepIcon`, 순서+역할), 근처 대안은 흰 바탕 글자 A~E(`altIcon`), 검색 위치는 파란 점(`targetIcon`).
- 코스를 고르면 지도 위에 "◀ 2위 / 7 ▶ · 전체 보기"(`.course-nav`): 목록을 내리지 않고 코스를 넘겨 보고, 전체 보기는 고른 것을 풀고 검색한 넓이로 돌아갑니다.
- **내 일정**(코스 상세, `lib/plan.ts`): 코스를 바탕으로 장소 바꾸기·**가고 싶은 곳 넣기**(단계 사이 "+ 여기에 가고 싶은 곳 넣기" → `GET /api/places/nearby` 로 앞 장소 근처 2km 에서 이름·업종으로 찾기)·빼기·순서 바꾸기(▲▼). **날짜·출발 시간**을 정하면 단계마다 도착–출발 시간(머무는 시간은 단계마다 고름, 걷는 시간은 직선 거리로 다시 어림)과 끝나는 시간, "📅 캘린더에 저장"(.ics, 단계마다 일정 하나). 이 브라우저에만 저장(`localStorage` `course-plan:{코스id}`, 예전 `course-swaps:` 저장분은 옮겨 옴). 원래 코스로 되돌리기는 장소·순서·머무는 시간만(날짜·출발 시간은 둠).
- **근처 대안**(코스 상세): 단계마다 "다른 카페/식당/구경거리 보기" → `GET /api/courses/{id}/steps/{order}/alternatives` 5곳(A~E, 지도에도). "이걸로 바꾸기"는 그 단계를 바꾸고 바뀐 구간의 걷는 시간을 다시 어림합니다(`walkMinutes`, 서버와 같은 식). 바꾼 내용은 **이 브라우저에만**(`localStorage` `course-swaps:{코스id}`, `lib/storage.ts`) 저장하고 "원래 장소로"·"원래 코스로"로 되돌립니다. 예전에는 모두 숫자 동그라미라 순위 "2"와 단계 "2"가 헷갈린다는 의견이 있었습니다. 사용자가 지도를 꽤 옮기면 지도 위에 "이 지역에서 다시 찾기"(화면이 옮긴 것은 치지 않음).
- 코스 상세: 단계 번호 핀 + 동선, 단계 카드(사진·주소·전화·네이버지도 링크), 번호를 누르면 지도가 그 장소로.

## 8. 코드 스타일

- 주석과 사용자에게 보이는 문구는 **한국어**로 씁니다.
- Java: 생성자 주입, DTO 는 `record`, 도메인별 패키지. 기능을 추가하면 `@WebMvcTest` + `@MockitoBean` 컨트롤러 테스트나 단위 테스트를 함께 씁니다(DB 없이 돌아가게).
- 오류 응답은 `{ "message": "화면에 보여줄 문장" }` 입니다(`ApiException` 을 상속).
- 화면: 함수 컴포넌트, 서버 호출은 `lib/api.ts` 를 거칩니다. 색은 `index.css` 의 변수를 씁니다(다크 모드 대응).
- 주변 코드의 스타일과 주석 밀도를 따르세요.

## 9. 배포

- 주소: **https://date-course.kimseongmin.co.kr** (nginx → `127.0.0.1:18290`). 힐링룸과 같은 `ai-server`(SSH 별칭), 같은 DB·Redis.
- **jar 하나**: 화면(`frontend/dist`)을 `processResources` 가 jar 의 `static/` 에 넣고(`backend/build.gradle`), 스프링이 화면과 API 를 함께 냅니다. 화면 주소(`/search`, `/courses/{번호}`, `/saved`)로 바로 들어오면 `SpaController` 가 `index.html` 을 줍니다. jar 이름은 `date-course.jar`.
- 서버 폴더
  ```
  ~/date-course/
  ├─ releases/{날짜}-{커밋}/date-course.jar   최근 5개 보관
  ├─ current → releases/…                     실행 중인 버전
  ├─ shared/.env                              운영 설정 (권한 600). 힐링룸 shared/.env 의 DB_*·REDIS_* + DB_SCHEMA·REDIS_KEY_PREFIX·TOUR_API_*·server.address=127.0.0.1
  └─ deploy/                                  저장소 deploy/ 복사본
  ```
- 실행: systemd `date-course` (Java: 힐링룸과 같은 `~/apps/jre17`), 로그는 `journalctl -u date-course -f`.
- **새 버전 배포 = `main` 에 푸시** (`.github/workflows/deploy.yml`, `backend/**`·`frontend/**` 가 바뀐 푸시만): 화면 lint·build → 백엔드 테스트(`-Pci`)·bootJar → 배포 전용 키로 jar 를 보내면 서버의 `deploy/ci-receive.sh` 가 새 release 저장 → current 교체 → 재시작 → `/api/health` 확인, **실패하면 이전 버전으로 자동 복구**. Actions 탭에서 수동 실행도 됩니다.
  - Secrets: `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`, `DEPLOY_HOST`, `DEPLOY_PORT`, `DEPLOY_USER`. Variables: `VITE_NAVER_MAP_KEY_ID`(화면 빌드에 들어감).
  - 배포 전용 키는 서버 `authorized_keys` 에 `restrict,command="/home/seongmin/date-course/deploy/ci-receive.sh"` 로 묶여 **배포 말고는 아무것도 못 합니다**. 힐링룸 배포 키와 따로입니다.
  - `deploy/ci-receive.sh` 를 고치면 서버에 직접 복사합니다: `scp deploy/ci-receive.sh ai-server:date-course/deploy/`
  - 손으로 배포·되돌리기: 저장소 루트에서 `bash deploy/deploy.sh`, `bash deploy/deploy.sh rollback`.
- **sudo 가 필요한 설치**(서비스 등록, 재시작 허용, nginx, 인증서)는 사람이 직접: `sudo bash ~/date-course/deploy/install-root.sh --allow-restart`. 여러 번 실행해도 안전하고, nginx 는 `nginx -t` 를 통과할 때만 적용합니다. AI 는 sudo 비밀번호를 다루지 않습니다.
- nginx 는 **`sites-available/date-course.kimseongmin.co.kr` 파일만** 이 앱 것입니다. 힐링룸·메인 도메인 설정 파일은 건드리지 않습니다. `/assets/`(해시 붙은 화면 파일)는 30일 캐시.
- 네이버 클라우드 콘솔 Maps Application 의 Web 서비스 URL 에 `https://date-course.kimseongmin.co.kr` 이 있어야 운영에서 지도가 뜹니다.
- 로컬 PC 의 서버도 같은 운영 DB 를 봅니다. 스케줄러(점수 계산)가 두 번 돌아도 결과는 같지만, 오래 켜 두지는 않습니다.

## 10. 진행 상황

- [x] 기획안 (`docs/기획안.md`), 동작 방식 (`docs/동작방식.md`)
- [x] 뼈대: 백엔드(헬스 체크), 프론트(화면 주소, 서버 연결 확인)
- [ ] 키 발급 (네이버 지도 Dynamic Map + 이용 한도, TourAPI)
- [x] DB 설계, V1·V2 운영 DB 적용 (힐링룸 DB 의 `date_course` 스키마)
- [x] 백엔드에 JPA 연결, 엔티티 9개 테이블 (validate 통과)
- [x] 장소 수집기: TourAPI, 상가(상권)정보 CSV, 중복 찾기 (서울 수집: 157,301곳, 두 출처에 다 있는 곳 446, 중복 후보 217)
- [x] 전국 수집 (2026-10-28, 상가정보 202606 + TourAPI): 920,698곳, 두 출처에 다 있는 곳 8,580, 중복 후보 973, 541MB. 상가정보 4분 46초, TourAPI 43분
- [x] Redis 캐시 (`JsonCache`, 힐링룸 계정·`healing-room:date-course:` 키), 키워드 목록 API
- [x] DNS `date-course` A 기록 (가비아, 서버 IP 확인)
- [x] 장소 키워드 자동으로 붙이기 (`--collect=keywords`)
- [x] 코스 자동 조합 (`--collect=courses`): 2026-10-29 전국 9,618개 (4단계 8,760, 3단계 858), 평균 246분, 53초
- [x] 목적지 카페 코스 1,406개, 동네 코스 805개, 코스가 없으면 검색 반경 넓히기
- [x] 품질 신호: 모범음식점 9,616곳(place_badge, V3), 코스 상세에 고른 이유
- [x] 순위 점수(매일 04:30), 코스 검색·상세 API (Redis 캐시)
- [ ] 반응(저장·좋아요·다녀왔어요) API, 관리자 큐레이션
- [x] 화면: 지도(하나만), 인기 지역·내 주변, 키워드, 코스 순위 목록·핀·동선, 코스 상세
- [x] 화면: 근처 대안 보기·바꾸기(브라우저에 저장), 코스 넘겨 보기(◀ ▶ 전체 보기)
- [x] 내 일정: 가고 싶은 곳 넣기·빼기·순서 바꾸기, 날짜·출발 시간·머무는 시간으로 시간표, 캘린더(.ics) 저장
- [ ] 화면: 저장·반응, 내 저장
- [x] 배포 준비: jar 하나(화면 포함), deploy/(systemd·nginx·ci-receive·install-root), GitHub Actions
- [x] 배포 (2026-10-29): 배포 키, `install-root.sh`(서비스·nginx·HTTPS), 첫 자동 배포 성공 → https://date-course.kimseongmin.co.kr
- [x] 지역 검색: 동네·명소(우리 데이터), 주소(네이버 Geocoding), 검색하면 바로 그 위치로
