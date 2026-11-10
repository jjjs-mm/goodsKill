#!/usr/bin/env bash
set -euo pipefail

TICKET_URL="${TICKET_URL:-http://127.0.0.1:54870}"
ORDER_URL="${ORDER_URL:-http://127.0.0.1:19081}"
SESSION_ID="${SESSION_ID:-1000}"
USER_ID="e2e-$(date +%s)"

response=$(curl -fsS -X POST "$TICKET_URL/api/ticket-sessions/$SESSION_ID/reservations" \
  -H 'Content-Type: application/json' \
  -H "X-User-Id: $USER_ID" \
  --data '{"userPhone":"13800138000","quantity":1}')
reservation_id=$(jq -er '.reservationId' <<<"$response")
echo "accepted reservation: $reservation_id"

for _ in {1..30}; do
  order=$(curl -fsS "$ORDER_URL/api/orders/by-reservation/$reservation_id" \
    -H "X-User-Id: $USER_ID" 2>/dev/null || true)
  status=$(jq -r '.status // empty' <<<"$order" 2>/dev/null || true)
  if [[ "$status" == "3" ]]; then
    jq '{reservationId,status,stateDesc,cancellationReason}' <<<"$order"
    exit 0
  fi
  sleep 1
done

echo "order did not reach timeout-cancelled status within 30 seconds" >&2
exit 1
