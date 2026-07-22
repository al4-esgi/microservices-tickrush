#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
NAMESPACE="${NAMESPACE:-tickrush}"
EVENT_ID="${EVENT_ID:-11111111-1111-1111-1111-111111111111}"
kafka_stopped=false

booking_sql() {
  kubectl -n "${NAMESPACE}" exec deployment/booking-db -- \
    psql -v ON_ERROR_STOP=1 -U booking -d bookingdb -At -c "$1"
}

payment_sql() {
  kubectl -n "${NAMESPACE}" exec deployment/payment-db -- \
    psql -v ON_ERROR_STOP=1 -U payment -d paymentdb -At -c "$1"
}

stock() {
  curl --fail-with-body -sS "${BASE_URL}/events/${EVENT_ID}" | \
    sed -n 's/.*"availableSeats":\([0-9]*\).*/\1/p'
}

restore_kafka() {
  if "${kafka_stopped}"; then
    echo "Restauration de Kafka après interruption de la démo..." >&2
    kubectl -n "${NAMESPACE}" scale deployment/kafka --replicas=1 >/dev/null
    kubectl -n "${NAMESPACE}" rollout status deployment/kafka --timeout=240s >/dev/null
    kafka_stopped=false
  fi
}
trap restore_kafka EXIT INT TERM

echo "0. Préconditions k3s"
kubectl -n "${NAMESPACE}" rollout status deployment/booking-service --timeout=120s >/dev/null
kubectl -n "${NAMESPACE}" rollout status deployment/payment-service --timeout=120s >/dev/null
kubectl -n "${NAMESPACE}" rollout status deployment/kafka --timeout=120s >/dev/null
stock_before="$(stock)"
test -n "${stock_before}"
echo "   services prêts, stock initial=${stock_before}"

echo "1. Arrêt du broker Kafka"
kubectl -n "${NAMESPACE}" scale deployment/kafka --replicas=0 >/dev/null
kafka_stopped=true
kubectl -n "${NAMESPACE}" wait --for=delete pod -l app=kafka --timeout=120s >/dev/null
test -z "$(kubectl -n "${NAMESPACE}" get pods -l app=kafka -o name)"
echo "   aucun pod Kafka actif"

echo "2. Création d'une réservation pendant la panne"
response="$(curl --max-time 15 --fail-with-body -sS -X POST "${BASE_URL}/reservations" \
  -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"${EVENT_ID}\",\"customerRef\":\"tp7-outbox@esgi.fr\",\"quantity\":1}")"
reservation_id="$(printf '%s' "${response}" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
test -n "${reservation_id}"
printf '%s' "${response}" | grep -q '"status":"PENDING"'
test "$(stock)" -eq $((stock_before - 1))
echo "   HTTP 201, reservationId=${reservation_id}, stock décrémenté"

echo "3. Preuve SQL : donnée métier commitée, publication en attente"
test "$(booking_sql "SELECT count(*) FROM reservations WHERE id = '${reservation_id}' AND status = 'PENDING';")" = "1"
test "$(booking_sql "SELECT count(*) FROM outbox WHERE aggregate_id = '${reservation_id}' AND event_type = 'SeatReserved' AND published_at IS NULL;")" = "1"
booking_sql "SELECT id, event_type, event_key, topic, COALESCE(published_at::text, 'EN_ATTENTE') FROM outbox WHERE aggregate_id = '${reservation_id}' ORDER BY id;"

for _ in $(seq 1 20); do
  if kubectl -n "${NAMESPACE}" logs deployment/booking-service -c booking-service --since=60s | \
      grep -q 'Outbox en attente'; then
    break
  fi
  sleep 1
done
kubectl -n "${NAMESPACE}" logs deployment/booking-service -c booking-service --since=90s | \
  grep 'Outbox en attente' | tail -1

echo "4. Redémarrage Kafka : aucun nouvel appel client"
kubectl -n "${NAMESPACE}" scale deployment/kafka --replicas=1 >/dev/null
kubectl -n "${NAMESPACE}" rollout status deployment/kafka --timeout=240s >/dev/null
kafka_stopped=false

current=""
for _ in $(seq 1 90); do
  current="$(curl --fail-with-body -sS "${BASE_URL}/reservations/${reservation_id}")"
  if printf '%s' "${current}" | grep -q '"status":"TICKET_ISSUED"'; then
    break
  fi
  sleep 1
done
printf '%s' "${current}" | grep -q '"status":"TICKET_ISSUED"'
printf '%s' "${current}" | grep -Eq '"ticketId":"[0-9a-f-]{36}"'

for _ in $(seq 1 30); do
  booking_pending="$(booking_sql "SELECT count(*) FROM outbox WHERE aggregate_id = '${reservation_id}' AND published_at IS NULL;")"
  payment_pending="$(payment_sql "SELECT count(*) FROM outbox WHERE aggregate_id = '${reservation_id}' AND published_at IS NULL;")"
  if test "${booking_pending}" = "0" && test "${payment_pending}" = "0"; then
    break
  fi
  sleep 1
done
test "${booking_pending}" = "0"
test "${payment_pending}" = "0"

echo "5. Preuve du rattrapage de bout en bout"
test "$(booking_sql "SELECT count(*) FROM outbox WHERE aggregate_id = '${reservation_id}' AND event_type = 'SeatReserved' AND published_at IS NOT NULL;")" = "1"
test "$(booking_sql "SELECT count(*) FROM outbox WHERE aggregate_id = '${reservation_id}' AND event_type = 'TicketIssued' AND published_at IS NOT NULL;")" = "1"
test "$(payment_sql "SELECT count(*) FROM outbox WHERE aggregate_id = '${reservation_id}' AND event_type = 'PaymentReceived' AND published_at IS NOT NULL;")" = "1"
test "$(booking_sql "SELECT count(*) FROM processed_events WHERE aggregate_id = '${reservation_id}';")" = "1"
test "$(stock)" -eq $((stock_before - 1))

echo "   booking-service/outbox"
booking_sql "SELECT id, event_type, topic, published_at FROM outbox WHERE aggregate_id = '${reservation_id}' ORDER BY id;"
echo "   payment-service/outbox"
payment_sql "SELECT id, event_type, topic, published_at FROM outbox WHERE aggregate_id = '${reservation_id}' ORDER BY id;"

seat_reserved_messages="$(kubectl -n "${NAMESPACE}" exec deployment/kafka -- \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:29092 \
  --topic booking.seat-reserved \
  --from-beginning \
  --timeout-ms 5000 2>/dev/null || true)"
test "$(printf '%s' "${seat_reserved_messages}" | grep -c "${reservation_id}" || true)" -ge 1

trap - EXIT INT TERM
echo "TP07 Outbox : réservation créée sans Kafka, puis saga rattrapée sans perte : OK"
