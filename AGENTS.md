# AGENTS.md: AI 작업 가이드

이 문서는 AI 코딩 도구(Claude Code, ChatGPT/Codex, Cursor 등)가 이 저장소에서 작업할 때 따라야 할 규칙과 맥락입니다.
사람이 읽어도 프로젝트 전체를 파악할 수 있게 작성합니다. 기획은 `docs/기획안.md` 에 있습니다.

## 1. 프로젝트 한 줄 요약

**데이트코스 추천**: 지도에서 지역과 키워드(분위기·활동·실내외·예산)를 고르면, 순위가 매겨진 데이트 코스와 근처 식당·카페를 보여주는 웹 서비스입니다. 주소 `https://date-course.kimseongmin.co.kr` (아직 배포 전).

- **로그인은 없습니다.** 저장·좋아요·다녀왔어요는 브라우저마다 만드는 방문자 id 로 구분합니다. 로그인은 관리자 화면에만 둡니다.
- 장소는 **공공데이터 상가(상권)정보와 한국관광공사 TourAPI** 두 곳에서 모아 중복을 합칩니다. 지도 서비스의 리뷰·별점을 긁어 오지 않습니다(약관 위반).
- **무료로만 운영합니다.** 그래서 쓰지 않는 것:
  - 카카오 API: 2026-07-21 부터 카카오맵 API 무료 쿼터는 계정의 첫 번째 앱에만 주고, 그 밖에는 결제 수단(비즈월렛)을 연결해야 합니다.
  - 네이버 검색 API: 2026-07 말 개발자센터 신규 신청이 닫혔고, 옮겨 간 NAVER API HUB 는 유료화가 예고됐습니다. 네이버지도 링크는 API 없이 검색 주소(`https://map.naver.com/p/search/{이름}`)로 겁니다.
- 지도는 **네이버 지도 Dynamic Map 하나만** 무료 이용량(대표 계정 1개에 한해 월 6,000,000건) 안에서 씁니다. 넘으면 바로 과금되므로 콘솔의 이용 한도를 그 이하(5,000,000건)로 걸어 둡니다.
- 순위는 **큐레이션(관리자 등록 코스) + 사용자 반응**에 키워드 일치·동선·출처 일치를 더한 점수입니다. 범위는 전국이고, 큐레이션이 없는 지역은 코스를 자동으로 조합합니다.

## 2. 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 백엔드 | Java 17, Spring Boot 4.1, Gradle(wrapper), Spring Web MVC, Spring Data JPA |
| DB | PostgreSQL 16. 힐링룸과 **같은 DB(`healing_room`)·계정**, 테이블은 전용 스키마 **`date_course`** 에만 (`currentSchema`, `hibernate.default_schema`) |
| 프론트엔드 | React 19, TypeScript, Vite, react-router. 지도는 네이버 지도 JavaScript API v3 (Dynamic Map, 예정) |
| 테스트 | JUnit 5, MockMvc, Mockito (`@WebMvcTest`, `@MockitoBean`) |

## 3. 폴더 구조

```
/
├─ AGENTS.md / CLAUDE.md / README.md / .editorconfig / .gitattributes (모든 글 파일 UTF-8, 줄바꿈 LF)
├─ docs/기획안.md                  ← 기획 (원본은 Claude 문서, 바뀌면 함께 고침)
├─ db/                             ← DB 스키마 SQL (수동 실행, 5절)
├─ backend/                        ← Spring Boot 앱 (Gradle 프로젝트 루트)
│  ├─ .env.example                 ← 환경변수 형식 (.env 는 git 제외)
│  └─ src/main/java/com/datecourse/
│     ├─ DateCourseApplication.java
│     ├─ domain/{이름}/            ← 도메인마다 controller / service / repository / entity / dto (예정: collector, admin)
│     │  ├─ place/                 Place, PlaceSource(출처별 원본), PlaceMergeCandidate(중복 후보), PlaceKeyword
│     │  │                         enum: PlaceCategory, PlaceSourceType(sbiz/tour), MergeStatus, KeywordOrigin
│     │  ├─ keyword/               Keyword, KeywordKind(mood/activity/setting/budget)
│     │  ├─ course/                Course(단계·키워드 포함), CourseStep, CourseKind(curated/auto), StepRole(spot/cafe/meal)
│     │  └─ reaction/              Reaction, ReactionKind(save/like/visited)
│     └─ global/
│        ├─ controller/            HealthController  GET /api/health
│        ├─ exception/             ApiException(공통 부모), GlobalExceptionHandler (→ { "message": ... })
│        └─ util/                  LowercaseEnumConverter (enum 을 DB 에 소문자로: 각 enum 의 DbConverter 가 상속)
└─ frontend/                       ← Vite + React 앱
   ├─ vite.config.ts               /api 를 백엔드(18290)로 넘김
   └─ src/
      ├─ App.tsx                   화면 주소(라우트)
      ├─ lib/api.ts                fetch 래퍼 (오류는 서버의 message 를 그대로)
      └─ pages/                    HomePage(/), SearchPage(/search), CoursePage(/courses/:id), SavedPage(/saved), NotFoundPage
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

## 6. 외부 API 규칙

- 키는 `backend/.env` 에만 둡니다(`.env.example` 참고). **공개 저장소이므로 키·비밀번호를 코드·문서·커밋 메시지에 쓰지 않습니다.**
- TourAPI 는 **서버(수집기)만** 부릅니다. 상가(상권)정보는 공공데이터포털에서 분기마다 CSV 파일을 받아 넣습니다(키 없음, 이용 제한 없음).
- 화면은 우리 API 와 네이버 지도만 씁니다. 지도 키(`frontend/.env` 의 `VITE_NAVER_MAP_KEY_ID`, 스크립트 `https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=…`)는 브라우저에 들어가므로 콘솔에 Web 서비스 URL 을 등록해 도메인을 제한합니다.
- 네이버 클라우드 Maps 는 **Dynamic Map 만** 신청합니다. Static Map, Directions(길찾기), Geocoding 은 과금 대상만 늘리므로 쓰지 않습니다. 지도는 화면을 열 때 한 번만 만들고, 페이지를 옮길 때 다시 만들지 않게 합니다(호출 수 아끼기).
- 결과를 얼마나 오래 저장해도 되는지는 각 서비스 약관을 따릅니다. 리뷰·별점 수집 금지.

## 7. 코드 스타일

- 주석과 사용자에게 보이는 문구는 **한국어**로 씁니다.
- Java: 생성자 주입, DTO 는 `record`, 도메인별 패키지. 기능을 추가하면 `@WebMvcTest` + `@MockitoBean` 컨트롤러 테스트나 단위 테스트를 함께 씁니다(DB 없이 돌아가게).
- 오류 응답은 `{ "message": "화면에 보여줄 문장" }` 입니다(`ApiException` 을 상속).
- 화면: 함수 컴포넌트, 서버 호출은 `lib/api.ts` 를 거칩니다. 색은 `index.css` 의 변수를 씁니다(다크 모드 대응).
- 주변 코드의 스타일과 주석 밀도를 따르세요.

## 8. 배포 (예정)

- 힐링룸과 같은 `ai-server`. nginx 가 `/` 는 `frontend/dist` 를, `/api` 는 `127.0.0.1:18290` 으로 넘깁니다. nginx 는 이 주소 전용 `sites-available` 파일만 다루고, 다른 서비스 설정은 건드리지 않습니다.
- `main` 푸시 → GitHub Actions 로 테스트·빌드·배포 (경로별: `backend/**`, `frontend/**`).

## 9. 진행 상황

- [x] 기획안 (`docs/기획안.md`)
- [x] 뼈대: 백엔드(헬스 체크), 프론트(화면 주소, 서버 연결 확인)
- [ ] 키 발급 (네이버 지도 Dynamic Map + 이용 한도, TourAPI)
- [x] DB 설계, V1·V2 운영 DB 적용 (힐링룸 DB 의 `date_course` 스키마)
- [x] 백엔드에 JPA 연결, 엔티티 9개 테이블 (validate 통과)
- [ ] 장소 수집기 (상가정보 CSV → TourAPI → 중복 제거)
- [ ] 코스 자동 조합과 순위 점수, 관리자 큐레이션
- [ ] 화면: 지도, 키워드, 순위 목록, 코스 상세
- [ ] 배포 (GitHub Actions, nginx, DNS)
