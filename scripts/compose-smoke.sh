#!/usr/bin/env bash
set -euo pipefail

export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-smoke-db-$(openssl rand -hex 16)}"
export MARKETGUARD_ADMIN_USERNAME="${MARKETGUARD_ADMIN_USERNAME:-smoke-operator}"
export MARKETGUARD_ADMIN_PASSWORD="${MARKETGUARD_ADMIN_PASSWORD:-smoke-admin-$(openssl rand -hex 16)}"
export MARKETGUARD_ALLOWED_ORIGINS="${MARKETGUARD_ALLOWED_ORIGINS:-http://localhost:5050}"
export COLLECTOR_ENABLED=false

cleanup() {
  docker compose down --volumes --remove-orphans
}
trap cleanup EXIT

docker compose up --build --detach --wait --wait-timeout 180

base_url="http://127.0.0.1:${HOST_PORT:-5050}"
curl --fail --silent --show-error "${base_url}/actuator/health/liveness" >/dev/null
curl --fail --silent --show-error "${base_url}/actuator/health/readiness" >/dev/null

unauthenticated_status="$(curl --silent --output /dev/null --write-out '%{http_code}' "${base_url}/api/anomalies")"
test "${unauthenticated_status}" = "401"

curl --fail --silent --show-error \
  --user "${MARKETGUARD_ADMIN_USERNAME}:${MARKETGUARD_ADMIN_PASSWORD}" \
  "${base_url}/" >/dev/null
curl --fail --silent --show-error \
  --user "${MARKETGUARD_ADMIN_USERNAME}:${MARKETGUARD_ADMIN_PASSWORD}" \
  "${base_url}/actuator/prometheus" | grep --quiet '^jvm_memory_used_bytes'

untrusted_origin_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
  --user "${MARKETGUARD_ADMIN_USERNAME}:${MARKETGUARD_ADMIN_PASSWORD}" \
  --header 'Origin: https://evil.example' "${base_url}/ws/info")"
test "${untrusted_origin_status}" = "403"

migration_count="$(docker compose exec --no-TTY postgres \
  psql --tuples-only --no-align --username "${POSTGRES_USER:-marketguard}" \
  --dbname "${POSTGRES_DB:-marketguard}" \
  --command 'select count(*) from flyway_schema_history where success = true')"
test "${migration_count}" = "3"
