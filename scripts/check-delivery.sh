#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

bash -n scripts/*.sh

for script in \
  scripts/check-delivery.sh \
  scripts/init-mysql.sh \
  scripts/start-admin.sh \
  scripts/start-compose.sh \
  scripts/start-mq.sh \
  scripts/smoke-admin.sh \
  scripts/stop-compose.sh
do
  test -x "${script}"
done

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  docker compose config >/dev/null
elif [[ "${REQUIRE_DOCKER:-false}" == "true" ]]; then
  echo "docker compose is required for delivery checks" >&2
  exit 1
else
  echo "docker compose not found, skip compose config check"
fi
