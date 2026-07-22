#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
NAMESPACE="${NAMESPACE:-tickrush}"
EVENT_ID="${EVENT_ID:-11111111-1111-1111-1111-111111111111}"

echo "1. Création d'une réservation à une place"
reservation="$(curl --fail-with-body -sS -X POST "${BASE_URL}/reservations" \
  -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"${EVENT_ID}\",\"customerRef\":\"tp5-demo@esgi.fr\",\"quantity\":1}")"
reservation_id="$(printf '%s' "${reservation}" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
test -n "${reservation_id}"
echo "   reservationId=${reservation_id}, statut initial=PENDING"

echo "2. Attente du pipeline SeatReserved -> PaymentReceived"
current=""
for _ in $(seq 1 20); do
  current="$(curl --fail-with-body -sS "${BASE_URL}/reservations/${reservation_id}")"
  if printf '%s' "${current}" | grep -q '"status":"PAID"'; then
    break
  fi
  sleep 1
done
printf '%s' "${current}" | grep -q '"status":"PAID"'
echo "   réservation PAID"

payment_status="$(curl --fail-with-body -sS \
  "${BASE_URL}/reservations/${reservation_id}/payment-status")"
printf '%s' "${payment_status}" | grep -q 'RECEIVED'
echo "   paiement RECEIVED"

echo "3. Rejeu du même PaymentReceived"
payment_row="$(kubectl -n "${NAMESPACE}" exec deployment/payment-db -- \
  psql -v ON_ERROR_STOP=1 -U payment -d paymentdb -At -F '|' \
  -c "SELECT id, amount FROM payments WHERE \"reservationId\" = '${reservation_id}';")"
payment_id="${payment_row%%|*}"
amount="${payment_row#*|}"
test -n "${payment_id}"
test -n "${amount}"

occurred_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
duplicate="$(printf \
  '{"eventId":"%s","eventType":"PaymentReceived","occurredAt":"%s","aggregateId":"%s","payload":{"paymentId":"%s","reservationId":"%s","amount":%s}}' \
  "${payment_id}" "${occurred_at}" "${reservation_id}" "${payment_id}" "${reservation_id}" "${amount}")"
printf '%s:%s\n' "${reservation_id}" "${duplicate}" | \
  kubectl -n "${NAMESPACE}" exec -i deployment/kafka -- \
    /opt/kafka/bin/kafka-console-producer.sh \
    --bootstrap-server kafka:29092 \
    --topic payment.received \
    --property parse.key=true \
    --property key.separator=: >/dev/null
sleep 3

marker_count="$(kubectl -n "${NAMESPACE}" exec deployment/booking-db -- \
  psql -v ON_ERROR_STOP=1 -U booking -d bookingdb -At \
  -c "SELECT count(*) FROM processed_events WHERE event_id = '${payment_id}';")"
test "${marker_count}" = "1"
after_replay="$(curl --fail-with-body -sS "${BASE_URL}/reservations/${reservation_id}")"
printf '%s' "${after_replay}" | grep -q '"status":"PAID"'
echo "   processed_events=${marker_count}, statut toujours PAID"

kubectl -n "${NAMESPACE}" logs deployment/booking-service --since=30s | \
  grep "Evenement deja traite: eventId=${payment_id}" | tail -1

echo "TP05 nominal + idempotence: OK"
