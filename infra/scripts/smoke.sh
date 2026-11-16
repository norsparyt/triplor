#!/bin/sh
# Runs the end-to-end smoke test (infra/k6/smoke.js) against the stack started by
# `make up-all`. k6 runs in a container on the Compose network, so nothing needs installing
# and the services are reached by their Compose names.
#
# Needs in .env: TRIPLOR_ADMIN_EMAIL and TRIPLOR_ADMIN_PASSWORD (the smoke test verifies its
# users through the admin API). Needs at least one place: `make sample-places`.
set -eu

cd "$(dirname "$0")/../.."

value_of() {
  grep -E "^$1=" .env | head -n 1 | cut -d= -f2-
}

ADMIN_EMAIL="$(value_of TRIPLOR_ADMIN_EMAIL)"
ADMIN_PASSWORD="$(value_of TRIPLOR_ADMIN_PASSWORD)"
if [ -z "$ADMIN_EMAIL" ] || [ -z "$ADMIN_PASSWORD" ]; then
  echo "Set TRIPLOR_ADMIN_EMAIL and TRIPLOR_ADMIN_PASSWORD in .env, then restart identity." >&2
  exit 1
fi

docker run --rm \
  --network triplor_default \
  -v "$PWD/infra/k6:/scripts:ro" \
  -e IDENTITY_URL=http://identity:8081 \
  -e ADVENTURE_URL=http://adventure:8082 \
  -e CHAT_URL=http://chat:8083 \
  -e ADMIN_EMAIL="$ADMIN_EMAIL" \
  -e ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  grafana/k6:1.8.3 run --quiet /scripts/smoke.js
