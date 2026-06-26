#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATABASE="${MYSQL_DATABASE:-framework_admin}"
MYSQL_USER_NAME="${MYSQL_USER_NAME:-root}"
MYSQL_PASSWORD_VALUE="${MYSQL_PASSWORD_VALUE:-root}"

mysql -u"${MYSQL_USER_NAME}" -p"${MYSQL_PASSWORD_VALUE}" -e "CREATE DATABASE IF NOT EXISTS ${DATABASE} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u"${MYSQL_USER_NAME}" -p"${MYSQL_PASSWORD_VALUE}" "${DATABASE}" < "${ROOT_DIR}/sql/mysql/framework_boot_starter_init.sql"
