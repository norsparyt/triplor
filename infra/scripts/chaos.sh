#!/bin/sh
# Controls Toxiproxy, which sits between adventure and chat in the stack started by
# `make up-chaos`.
#
#   infra/scripts/chaos.sh slow 500   every answer from chat is delayed by 500 ms
#   infra/scripts/chaos.sh reset      remove the delay
#   infra/scripts/chaos.sh status     show the proxy and its active faults
set -eu

API="http://localhost:8474"

case "${1:-status}" in
  slow)
    MS="${2:-500}"
    curl -fsS -o /dev/null -X DELETE "$API/proxies/chat/toxics/slow" 2>/dev/null || true
    curl -fsS -o /dev/null -X POST "$API/proxies/chat/toxics" \
      -H 'Content-Type: application/json' \
      -d "{\"name\": \"slow\", \"type\": \"latency\", \"stream\": \"downstream\", \"attributes\": {\"latency\": $MS}}"
    echo "Chat now answers adventure $MS ms late."
    ;;
  reset)
    curl -fsS -o /dev/null -X DELETE "$API/proxies/chat/toxics/slow" 2>/dev/null || true
    echo "Delay removed."
    ;;
  status)
    curl -fsS "$API/proxies/chat"
    echo
    ;;
  *)
    echo "usage: $0 slow [milliseconds] | reset | status" >&2
    exit 1
    ;;
esac
