#!/usr/bin/env bash
# 손으로 배포·되돌리기 (보통은 main 에 푸시하면 GitHub Actions 가 배포한다)
#
#   bash deploy/deploy.sh            # 저장소 루트에서: 화면·백엔드 빌드 → 새 release 로 올림 → current 교체 → 재시작 → 확인
#   bash deploy/deploy.sh rollback   # 바로 이전 release 로 되돌리기
#
# 필요: ~/.ssh/config 의 Host ai-server, 서버에 install-root.sh --allow-restart 를 한 번 실행해 둔 상태
set -euo pipefail

HOST=ai-server
APP_DIR=date-course
KEEP=5   # 서버에 남겨 둘 release 수

restart_and_check() {
  ssh "$HOST" 'sudo -n /usr/bin/systemctl restart date-course \
    && for i in $(seq 1 45); do python3 -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen(\"http://127.0.0.1:18290/api/health\", timeout=3).status == 200 else 1)" 2>/dev/null && break; sleep 2; done \
    && systemctl is-active date-course \
    && python3 -c "import urllib.request; print(\"API\", urllib.request.urlopen(\"http://127.0.0.1:18290/api/health\", timeout=5).status)"'
}

if [ "${1:-}" = "rollback" ]; then
  ssh "$HOST" "cd ~/$APP_DIR && prev=\$(ls -1 releases | sort | tail -2 | head -1) && ln -sfn releases/\$prev current && echo \"되돌림: \$prev\""
  restart_and_check
  exit 0
fi

cd "$(dirname "$0")/.."
if [ -n "$(git status --porcelain)" ]; then
  echo "커밋하지 않은 변경이 있어요. 배포할 내용을 먼저 커밋하세요."; exit 1
fi
(cd frontend && npm ci --silent && npm run build)
(cd backend && ./gradlew clean test bootJar -Pci --console=plain -q)

release="$(date +%Y%m%d%H%M%S)-$(git rev-parse --short HEAD)"
ssh "$HOST" "mkdir -p ~/$APP_DIR/releases/$release"
scp -q backend/build/libs/date-course.jar "$HOST:$APP_DIR/releases/$release/date-course.jar"
ssh "$HOST" "cd ~/$APP_DIR && ln -sfn releases/$release current \
  && ls -1 releases | sort | head -n -$KEEP | xargs -r -I{} rm -rf releases/{}"
echo "release: $release"
restart_and_check
