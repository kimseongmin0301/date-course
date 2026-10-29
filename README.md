# 데이트코스 추천

지도에서 지역과 분위기를 고르면, **순위가 매겨진 데이트 코스**(구경 → 카페 → 식당)를 지도 위 동선과 함께 보여주는 웹 서비스입니다.

**https://date-course.kimseongmin.co.kr**

> 예: 성수동 + 감성 → 스탈릿성수 → 성수동 카페거리 → 카페 → 식당 (약 4시간, 걸어서 이동)

- 로그인 없이 바로 씁니다.
- 전국 장소 **920,698곳**(공공데이터 상가정보 + 한국관광공사 TourAPI)을 바탕으로 코스 **10,665개**를 미리 만들어 두었습니다.
- 무료 서비스만 씁니다(네이버 지도 Dynamic Map·Geocoding 무료 이용량 안).

## 할 수 있는 것

| 기능 | 설명 |
|---|---|
| 지역 고르기 | 검색(동네 "성수동"·역 "반석역"·명소 "경복궁"·주소 "강남대로 396"), 지도 옮기기, 인기 지역 15곳, 내 주변 |
| 키워드 | 분위기(조용·로맨틱·활기찬·감성·이색적), 활동(산책·전시·체험·쇼핑·액티비티·야경·맛집 투어), 실내외, 예산 중 3개까지 |
| 코스 순위 | 지도 가운데 반경 안의 코스를 순위대로. 지도에는 순위 핀, 코스를 고르면 단계 핀과 동선 |
| 코스 상세 | 단계마다 사진·주소·전화·네이버지도 링크, 단계 사이 걷는 시간 |
| 코스가 없는 동네 | 명소가 없는 동네는 소품샵·서점·놀거리 중심의 "동네 코스", 그래도 없으면 반경을 넓혀 찾음 |

## 어떻게 추천하나

1. **장소 모으기**: 상가(상권)정보 CSV(카페·식당·소품샵·놀거리)와 TourAPI(관광지·문화시설)를 합치고 같은 장소는 하나로 묶습니다.
2. **키워드 붙이기**: 업종·분류·이름으로 장소마다 키워드를 자동으로 붙입니다(미술관 → 전시·조용·감성).
3. **코스 만들기**: 명소 하나를 중심으로 걸어서 15분 안의 구경거리 → 카페 → 식당을 골라 잇습니다. 분위기가 맞고 체인점이 아닌 곳을 먼저 고릅니다.
4. **순위 매기기**: 동선·출처 일치·사용자 반응으로 코스 점수를 매기고, 검색할 때 고른 키워드와 거리를 더합니다.

자세한 규칙은 [docs/동작방식.md](docs/동작방식.md)에 있습니다.

## 문서

| 문서 | 내용 |
|---|---|
| [docs/기획안.md](docs/기획안.md) | 무엇을 왜 만드는지, 대상, 화면, 정한 것과 남은 일 |
| [docs/동작방식.md](docs/동작방식.md) | 데이터 수집부터 코스 조합·순위·검색까지 실제 규칙 |
| [AGENTS.md](AGENTS.md) | 저장소 규칙, 폴더 구조, API, 운영·배포 (AI 도구와 사람 모두를 위한 작업 가이드) |

## 기술

| 영역 | 사용 기술 |
|---|---|
| 백엔드 | Java 17, Spring Boot 4.1 (Web MVC, JPA, JdbcClient), 포트 18290 |
| DB·캐시 | PostgreSQL 16 (전용 스키마 `date_course`), Redis 7 |
| 화면 | React 19, TypeScript, Vite, 네이버 지도 JavaScript API v3 |
| 배포 | GitHub Actions → 서버(systemd, nginx, Let's Encrypt). 화면은 jar 안에 함께 들어갑니다 |

```
backend/    Spring Boot API 서버 + 데이터 수집기 (화면 빌드 결과도 jar 에 넣어 함께 서빙)
frontend/   React 화면
db/         DB 스키마 (사람이 직접 실행하는 마이그레이션)
deploy/     서버 설치·배포 스크립트 (systemd, nginx)
docs/       기획·동작 방식 문서
```

## 로컬 실행

필요한 것: Java 17, Node 22, PostgreSQL·Redis 접속 정보, (선택) 네이버 지도·TourAPI 키

```bash
cd backend
cp .env.example .env   # DB·Redis·키 값 넣기
./gradlew bootRun      # http://localhost:18290
```

```bash
cd frontend
cp .env.example .env   # 네이버 지도 Client ID
npm install
npm run dev            # http://localhost:5173 (/api 는 백엔드로)
```

- 테스트: `cd backend && ./gradlew test -Pci` (DB 없이), 화면은 `npm run build && npm run lint`
- DB 스키마는 앱이 만들지 않습니다. `db/V*.sql` 을 순서대로 직접 실행하세요(`ddl-auto=validate`).

## 데이터 넣기

```bash
cd backend
./gradlew bootRun --args="--collect=sbiz --path=../data/sbiz/202606"   # 상가(상권)정보 CSV (약 5분)
./gradlew bootRun --args="--collect=tour"                              # TourAPI 전국 (약 40분)
```

수집이 끝나면 키워드 → 코스 → 순위 점수까지 저절로 다시 만듭니다. 규칙만 바꿨을 때는 `--collect=keywords`, `--collect=courses`, `--collect=scores` 로 그 단계부터 다시 돌립니다.

상가(상권)정보는 [공공데이터포털](https://www.data.go.kr/data/15083033/fileData.do)에서 분기마다 받아 `data/sbiz/{기준월}/` 에 풉니다(저장소에는 올리지 않음).

## 배포

`main` 에 푸시하면 GitHub Actions 가 화면 검사·빌드 → 백엔드 테스트·빌드 → 서버 전송·재시작·상태 확인까지 하고, 실패하면 이전 버전으로 되돌립니다. 서버 구성은 [AGENTS.md 9절](AGENTS.md#9-배포)을 보세요.

## 데이터 출처

- 소상공인시장진흥공단 상가(상권)정보 (공공데이터포털)
- 한국관광공사 국문 관광정보 서비스 TourAPI (공공데이터포털)
- 지도·주소 검색: 네이버 클라우드 Maps

리뷰·별점은 수집하지 않습니다. 영업 여부는 네이버지도에서 확인하도록 안내합니다.
