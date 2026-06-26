#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"

if [[ -f "${ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi

DATABASE="${MYSQL_DATABASE:-framework_admin}"
MYSQL_ADMIN_USER="${MYSQL_ROOT_USER:-${MYSQL_USER_NAME:-root}}"
MYSQL_ADMIN_PASSWORD="${MYSQL_ROOT_PASSWORD:-${MYSQL_PASSWORD_VALUE:-root}}"

if [[ ! "${DATABASE}" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "MYSQL_DATABASE must match [A-Za-z0-9_]+, got: ${DATABASE}" >&2
  exit 1
fi

MYSQL_PWD="${MYSQL_ADMIN_PASSWORD}" mysql -u"${MYSQL_ADMIN_USER}" \
  -e "CREATE DATABASE IF NOT EXISTS \`${DATABASE}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
MYSQL_PWD="${MYSQL_ADMIN_PASSWORD}" mysql -u"${MYSQL_ADMIN_USER}" "${DATABASE}" \
  < "${ROOT_DIR}/sql/mysql/framework_boot_starter_init.sql"
