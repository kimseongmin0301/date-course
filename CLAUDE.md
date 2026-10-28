# CLAUDE.md

프로젝트 규칙과 맥락은 AGENTS.md 에 있습니다. (ChatGPT/Codex 등 다른 도구와 같은 문서를 공유하기 위해 분리)

@AGENTS.md

## Claude Code 전용 메모

- 백엔드 실행은 `backend/` 에서 `./gradlew bootRun`, 화면은 `frontend/` 에서 `npm run dev`. 미리보기용 `.claude/launch.json` 은 git 에서 제외되어 있다.
- **사용자에게 보이는 모든 글(답변, 도구 쓰기 전 안내, 중간 보고)은 한국어로만 쓴다.** 코드 식별자·명령어·경로만 원문.
- `.env` 의 값(API 키, 비밀번호)은 대화나 커밋 메시지에 옮겨 적지 않는다. **공개 저장소**다.
- DB 마이그레이션(db/*.sql)을 실행할 때는 사용자에게 먼저 알리고, `SET lock_timeout = '5s'` 를 건다.
- 운영 서버(`ssh ai-server`)는 힐링룸과 함께 쓴다. 힐링룸의 폴더·서비스·nginx 파일은 건드리지 않는다.
- 화면 코드를 고친 뒤에는 `npm run build`(타입 검사 포함)와 `npm run lint` 로 확인한다.
