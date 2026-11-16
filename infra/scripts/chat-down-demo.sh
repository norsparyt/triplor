#!/bin/sh
# Failure demonstration for Phase 1: stops chat, accepts a traveller, starts chat, retries.
# Shows the weak spot of the synchronous call: the organizer gets a 502 although the seat was
# taken, and has to accept again. Needs the stack from `make up-load`.
set -eu

cd "$(dirname "$0")/../.."
COMPOSE="docker compose --env-file .env -f infra/compose/compose.yaml --profile app"

echo "== Stopping chat"
$COMPOSE stop chat

echo "== Accepting while chat is down"
OUTPUT="$(PHASE=down infra/scripts/load.sh chat-down --quiet 2>&1)" || { echo "$OUTPUT"; $COMPOSE start chat; exit 1; }
echo "$OUTPUT" | grep -E "✓|✗|accept answered"
STATE="$(echo "$OUTPUT" | grep -o 'DEMO_STATE [0-9a-f-]* [0-9a-f-]*' | head -n 1)"
ADVENTURE_ID="$(echo "$STATE" | cut -d' ' -f2)"
REQUEST_ID="$(echo "$STATE" | cut -d' ' -f3)"

echo "== Starting chat again"
# `start`, not `up`: the container is started as it was, and nothing else is touched.
$COMPOSE start chat
CHAT_CONTAINER="$($COMPOSE ps -q chat)"
until [ "$(docker inspect --format '{{.State.Health.Status}}' "$CHAT_CONTAINER")" = "healthy" ]; do
  sleep 1
done

echo "== Accepting the same request again"
PHASE=retry ADVENTURE_ID="$ADVENTURE_ID" REQUEST_ID="$REQUEST_ID" infra/scripts/load.sh chat-down --quiet 2>&1 | grep -E "✓|✗|retry succeeded"
