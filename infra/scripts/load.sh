#!/bin/sh
# Runs one of the k6 load tests in infra/k6 against the stack started by `make up-load`
# (or `make up-chaos`).
#
#   infra/scripts/load.sh search
#   infra/scripts/load.sh accept
#   VUS=20 DURATION=2m infra/scripts/load.sh accept
#
# k6 runs in a container on the Compose network. It logs in as the load-test users that
# identity's `seed` profile creates, with TRIPLOR_SEED_PASSWORD from .env.
set -eu

cd "$(dirname "$0")/../.."

SCRIPT="${1:?usage: $0 search|accept|chat-down}"
shift

SEED_PASSWORD="$(grep -E '^TRIPLOR_SEED_PASSWORD=' .env | head -n 1 | cut -d= -f2-)"
if [ -z "$SEED_PASSWORD" ]; then
  echo "Set TRIPLOR_SEED_PASSWORD in .env and start the stack with make up-load." >&2
  exit 1
fi

docker run --rm \
  --network triplor_default \
  -v "$PWD/infra/k6:/scripts:ro" \
  -e IDENTITY_URL=http://identity:8081 \
  -e ADVENTURE_URL=http://adventure:8082 \
  -e CHAT_URL=http://chat:8083 \
  -e SEED_PASSWORD="$SEED_PASSWORD" \
  -e USERS="${USERS:-200}" \
  -e VUS="${VUS:-}" \
  -e DURATION="${DURATION:-}" \
  -e PHASE="${PHASE:-}" \
  -e ADVENTURE_ID="${ADVENTURE_ID:-}" \
  -e REQUEST_ID="${REQUEST_ID:-}" \
  grafana/k6:1.8.3 run "$@" "/scripts/$SCRIPT.js"
