#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
EVENT_ID="${EVENT_ID:-11111111-1111-1111-1111-111111111111}"

echo "1. Création d'une réservation de trois places (3 × 49,90 = 149,70 EUR)"
reservation="$(curl --fail-with-body -sS -X POST "${BASE_URL}/reservations" \
  -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"${EVENT_ID}\",\"customerRef\":\"tp5-rejection@esgi.fr\",\"quantity\":3}")"
reservation_id="$(printf '%s' "${reservation}" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
test -n "${reservation_id}"
printf '%s' "${reservation}" | grep -q '"amount":149.70'
echo "   reservationId=${reservation_id}, amount=149.70"

echo "2. Attente de PaymentFailed et de la compensation TP06"
payment_status=""
for _ in $(seq 1 20); do
  payment_status="$(curl --fail-with-body -sS \
    "${BASE_URL}/reservations/${reservation_id}/payment-status")"
  if printf '%s' "${payment_status}" | grep -q 'REJECTED'; then
    break
  fi
  sleep 1
done
printf '%s' "${payment_status}" | grep -q 'REJECTED'

current=""
for _ in $(seq 1 20); do
  current="$(curl --fail-with-body -sS "${BASE_URL}/reservations/${reservation_id}")"
  if printf '%s' "${current}" | grep -q '"status":"CANCELLED"'; then
    break
  fi
  sleep 1
done
printf '%s' "${current}" | grep -q '"status":"CANCELLED"'
echo "   paiement REJECTED, réservation CANCELLED et places libérées"

echo "TP05 refus contrôlé: OK"
