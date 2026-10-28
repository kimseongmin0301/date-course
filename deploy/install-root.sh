#!/usr/bin/env bash
# 데이트코스 서버 설치 (sudo 가 필요한 부분만). 여러 번 실행해도 안전하다.
#
#   sudo bash ~/date-course/deploy/install-root.sh                 # 기본
#   sudo bash ~/date-course/deploy/install-root.sh --allow-restart # + 배포 때 비밀번호 없이 재시작 허용
#
# 하는 일
#   1) systemd 서비스(date-course) 등록·시작
#   2) nginx 에 date-course.kimseongmin.co.kr 사이트 파일 "추가" (힐링룸·기존 kimseongmin.co.kr 설정은 건드리지 않음)
#   3) DNS 가 이 서버를 가리키면 Let's Encrypt 인증서 발급
#   4) HTTPS 설정으로 교체
# nginx 설정은 항상 `nginx -t` 검사를 통과할 때만 적용하고, 실패하면 이전 상태로 되돌린다.
set -euo pipefail

DOMAIN=date-course.kimseongmin.co.kr
APP_PORT=18290
HERE=$(cd "$(dirname "$0")" && pwd)
SITE=/etc/nginx/sites-available/$DOMAIN
LINK=/etc/nginx/sites-enabled/$DOMAIN
CERT=/etc/letsencrypt/live/$DOMAIN/fullchain.pem

step() { echo; echo "===== $* ====="; }
[ "$(id -u)" -eq 0 ] || { echo "sudo 로 실행해 주세요: sudo bash $0"; exit 1; }

# nginx 사이트 파일을 바꾸고 검사한다. 검사에 실패하면 원래대로 되돌린다
apply_nginx() {
  local conf=$1 backup=""
  if [ -f "$SITE" ]; then backup=$(mktemp); cp "$SITE" "$backup"; fi
  install -m 644 "$conf" "$SITE"
  ln -sfn "$SITE" "$LINK"
  if nginx -t 2>&1; then
    systemctl reload nginx
    echo "nginx 적용 완료: $(basename "$conf")"
  else
    echo "!! nginx 설정 검사 실패 → 되돌립니다"
    if [ -n "$backup" ]; then cp "$backup" "$SITE"; else rm -f "$LINK" "$SITE"; fi
    nginx -t && systemctl reload nginx
    exit 1
  fi
}

step "1/4 앱 서비스 등록"
install -m 644 "$HERE/date-course.service" /etc/systemd/system/date-course.service
systemctl daemon-reload
systemctl enable --quiet date-course
systemctl restart date-course
for _ in $(seq 1 30); do ss -ltn | grep -q ":$APP_PORT " && break; sleep 2; done
if ss -ltn | grep -q ":$APP_PORT "; then
  echo "date-course 실행 중 (127.0.0.1:$APP_PORT)"
else
  echo "!! 앱이 시작되지 않았어요. 로그: journalctl -u date-course -n 50"; exit 1
fi

if [ "${1:-}" = "--allow-restart" ]; then
  # 배포할 때 seongmin 계정이 비밀번호 없이 "이 서비스 재시작"만 할 수 있게 한다 (다른 명령은 그대로 비밀번호 필요)
  tmp=$(mktemp)
  echo "seongmin ALL=(root) NOPASSWD: /usr/bin/systemctl restart date-course" > "$tmp"
  visudo -cf "$tmp" >/dev/null && install -m 440 "$tmp" /etc/sudoers.d/date-course
  rm -f "$tmp"
  echo "재시작 허용 등록: /etc/sudoers.d/date-course"
fi

step "2/4 nginx 사이트 추가"
if [ -f "$CERT" ]; then
  apply_nginx "$HERE/nginx-https.conf"
else
  apply_nginx "$HERE/nginx-http.conf"
fi

step "3/4 HTTPS 인증서"
resolved=$(getent ahostsv4 "$DOMAIN" | awk 'NR==1{print $1}' || true)
# 이 서버의 공인 IP (공유기 뒤에 있으면 서버 안에서는 사설 IP 만 보이므로 밖에 물어본다)
SERVER_IP=$(python3 -c 'import urllib.request; print(urllib.request.urlopen("https://api.ipify.org", timeout=5).read().decode())' 2>/dev/null || true)
if [ -z "$resolved" ] || { [ -n "$SERVER_IP" ] && [ "$resolved" != "$SERVER_IP" ]; }; then
  echo "DNS 가 아직 이 서버를 가리키지 않아요 ($DOMAIN → ${resolved:-없음})."
  echo "도메인 관리 화면에서 date-course 레코드(A: ${SERVER_IP:-이 서버 공인 IP} 또는 CNAME)를 추가하고, 반영된 뒤 이 스크립트를 다시 실행하세요."
  exit 0
fi
if [ ! -f "$CERT" ]; then
  certbot certonly --webroot -w /var/www/certbot -d "$DOMAIN" \
    --non-interactive --agree-tos --keep-until-expiring \
    --deploy-hook "systemctl reload nginx"
fi

step "4/4 HTTPS 적용"
apply_nginx "$HERE/nginx-https.conf"
echo
echo "완료: https://$DOMAIN"
