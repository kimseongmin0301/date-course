# 데이트코스 추천

지역과 분위기만 고르면, 순위가 매겨진 데이트 코스와 근처 식당·카페를 지도에서 바로 보여주는 웹 서비스입니다.

> 예: 강남 + 조용 → 별마당도서관 → 소품샵 → 카페 → 식당

- 기획: [docs/기획안.md](docs/기획안.md)
- AI 도구로 작업할 때의 규칙: [AGENTS.md](AGENTS.md)

## 구조

| 폴더 | 내용 |
|---|---|
| `backend/` | Spring Boot 4.1 (Java 17) API 서버, 포트 18290 |
| `frontend/` | React + TypeScript + Vite 화면 |
| `docs/` | 기획 문서 |

## 로컬 실행

```bash
# 백엔드
cd backend
cp .env.example .env   # 외부 API 키 넣기 (없어도 실행은 됨)
./gradlew bootRun

# 화면 (다른 터미널)
cd frontend
npm install
npm run dev            # http://localhost:5173
```
