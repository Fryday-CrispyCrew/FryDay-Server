#!/usr/bin/env bash
# deploy.yml이 ssh로 서버에 넘겨 실행한다.
# 필요한 변수: DEPLOY_DIR, IMAGE_TAG, APP_PORT, GHCR_USER, GHCR_TOKEN
set -euo pipefail

IMAGE=ghcr.io/fryday-crispycrew/fryday
HEALTH_URL="http://localhost:${APP_PORT}/actuator/health"
HEALTH_TIMEOUT=180
KEEP_IMAGES=3

cd ~/"$DEPLOY_DIR"
trap 'docker logout ghcr.io >/dev/null' EXIT
echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin

# DOWN이면 actuator가 503을 주므로 curl -f 실패로 판정된다.
wait_healthy() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT))
  until curl -fsS -o /dev/null "$HEALTH_URL" 2>/dev/null; do
    ((SECONDS < deadline)) || return 1
    sleep 5
  done
}

previous_tag=""
container=$(docker compose ps -q app)
if [ -n "$container" ]; then
  previous_image=$(docker inspect --format '{{.Config.Image}}' "$container")
  previous_tag=${previous_image##*:}
fi

export APP_TAG=$IMAGE_TAG
docker compose pull app
docker compose up -d app

if wait_healthy; then
  echo "배포 완료: $IMAGE_TAG"
  # 롤백용으로 최근 이미지 몇 개만 남긴다. docker images 기본 정렬은 생성 순서를 보장하지 않아 직접 정렬한다.
  docker images "$IMAGE" --format '{{.CreatedAt}}|{{.Tag}}' | sort -r | cut -d'|' -f2 \
    | tail -n +$((KEEP_IMAGES + 1)) \
    | grep -vxF -e "$IMAGE_TAG" -e "${previous_tag:-$IMAGE_TAG}" \
    | sed "s|^|$IMAGE:|" \
    | xargs -r docker image rm >/dev/null 2>&1 || true
  exit 0
fi

echo "::error::$IMAGE_TAG 가 ${HEALTH_TIMEOUT}초 안에 정상 기동하지 않았다"
docker compose logs --tail 100 app || true

if [ -z "$previous_tag" ] || [ "$previous_tag" = "$IMAGE_TAG" ]; then
  echo "::error::되돌릴 이전 버전이 없다"
  exit 1
fi

echo "이전 버전으로 롤백: $previous_tag"
export APP_TAG=$previous_tag
docker compose up -d app
if wait_healthy; then
  echo "::warning::$previous_tag 로 롤백했다"
else
  echo "::error::롤백한 $previous_tag 도 정상 기동하지 않았다"
fi
exit 1
