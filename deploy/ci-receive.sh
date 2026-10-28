#!/usr/bin/env bash
# GitHub Actions 배포 받기 (서버: ~/date-course/deploy/ci-receive.sh)
#
# 배포 전용 키의 authorized_keys 줄에 command="…/ci-receive.sh" 로 묶여 있어서, 그 키로 접속하면 무엇을 요청하든
# 이 스크립트만 실행된다. 쉘을 열거나 다른 명령을 실행할 수 없다.
#
#   ssh -i deploy_key -p {SSH포트} {계정}@{서버} "deploy 20261015103000-abc1234" < date-course.jar
#
# 하는 일: jar 받기 → 새 release 에 저장 → current 교체 → 재시작 → 상태 확인 (실패하면 이전 버전으로 되돌림)
set -euo pipefail

APP=/home/seongmin/date-course
KEEP=5
MAX_BYTES=300000000

read -r action release extra <<< "${SSH_ORIGINAL_COMMAND:-}"
if [[ "${action:-}" != "deploy" || ! "${release:-}" =~ ^[0-9]{14}-[0-9a-f]{7,40}$ || -n "${extra:-}" ]]; then
  echo "허용되지 않은 명령이에요. 사용법: deploy {YYYYmmddHHMMSS}-{커밋}" >&2
  exit 1
fi

dir="$APP/releases/$release"
if [ -e "$dir" ]; then echo "이미 있는 release 예요: $release" >&2; exit 1; fi
mkdir -p "$dir"

# 받기 (너무 크면 잘라서 아래 검사에서 걸리게 한다)
head -c "$MAX_BYTES" > "$dir/date-course.jar.part"
size=$(stat -c %s "$dir/date-course.jar.part")
if [ "$size" -lt 1000000 ] || [ "$size" -ge "$MAX_BYTES" ] || [ "$(head -c 2 "$dir/date-course.jar.part")" != "PK" ]; then
  rm -rf "$dir"
  echo "올바른 jar 가 아니에요 (크기 ${size} bytes)" >&2
  exit 1
fi
mv "$dir/date-course.jar.part" "$dir/date-course.jar"
echo "받음: $release ($((size / 1024 / 1024))MB)"

healthy() {
  for _ in $(seq 1 45); do
    if python3 -c 'import urllib.request,sys; sys.exit(0 if urllib.request.urlopen("http://127.0.0.1:18290/api/health", timeout=3).status == 200 else 1)' 2>/dev/null; then
      return 0
    fi
    sleep 2
  done
  return 1
}

previous=$(readlink "$APP/current" || true)
ln -sfn "releases/$release" "$APP/current"
sudo -n /usr/bin/systemctl restart date-course

if healthy; then
  echo "배포 완료: $release"
else
  echo "!! 새 버전이 정상적으로 뜨지 않아 이전 버전(${previous:-없음})으로 되돌립니다" >&2
  if [ -n "$previous" ]; then
    ln -sfn "$previous" "$APP/current"
    sudo -n /usr/bin/systemctl restart date-course
    healthy && echo "이전 버전으로 복구됨" >&2
  fi
  exit 1
fi

# 오래된 release 정리 (최근 KEEP 개 + 지금 쓰는 것은 남김)
current=$(basename "$(readlink "$APP/current")")
ls -1t "$APP/releases" | tail -n +$((KEEP + 1)) | while read -r old; do
  [ "$old" != "$current" ] && rm -rf "${APP:?}/releases/$old"
done
