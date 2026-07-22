#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
NAMESPACE="${NAMESPACE:-tickrush}"
EVENT_ID="${EVENT_ID:-11111111-1111-1111-1111-111111111111}"

original_ttl="$(kubectl -n "${NAMESPACE}" get deployment/booking-service \
  -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="RESERVATION_TTL_SECONDS")].value}')"
original_replicas="$(kubectl -n "${NAMESPACE}" get deployment/payment-service \
  -o jsonpath='{.spec.replicas}')"
original_ttl="${original_ttl:-120}"
original_replicas="${original_replicas:-1}"

restore() {
  set +e
  kubectl -n "${NAMESPACE}" set env deployment/booking-service \
    RESERVATION_TTL_SECONDS="${original_ttl}" >/dev/null
  kubectl -n "${NAMESPACE}" rollout restart deployment/booking-service >/dev/null
  kubectl -n "${NAMESPACE}" scale deployment/payment-service \
    --replicas="${original_replicas}" >/dev/null
  kubectl -n "${NAMESPACE}" rollout status deployment/booking-service --timeout=180s >/dev/null
  if [ "${original_replicas}" -gt 0 ]; then
    kubectl -n "${NAMESPACE}" rollout status deployment/payment-service --timeout=180s >/dev/null
  fi
}
trap restore EXIT

wait_for_no_payment_pod() {
  for _ in $(seq 1 60); do
    if [ -z "$(kubectl -n "${NAMESPACE}" get pod -l app=payment-service -o name)" ]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

stock() {
  curl --fail-with-body -sS "${BASE_URL}/events/${EVENT_ID}" | \
    sed -n 's/.*"availableSeats":\([0-9]*\).*/\1/p'
}

echo "1. Préparation : TTL=5s et payment-service arrêté"
kubectl -n "${NAMESPACE}" set env deployment/booking-service \
  RESERVATION_TTL_SECONDS=5 >/dev/null
kubectl -n "${NAMESPACE}" rollout restart deployment/booking-service >/dev/null
kubectl -n "${NAMESPACE}" rollout status deployment/booking-service --timeout=180s >/dev/null
test "$(kubectl -n "${NAMESPACE}" exec deployment/booking-service -- \
  printenv RESERVATION_TTL_SECONDS)" = "5"
kubectl -n "${NAMESPACE}" scale deployment/payment-service --replicas=0 >/dev/null
kubectl -n "${NAMESPACE}" rollout status deployment/payment-service --timeout=60s >/dev/null
wait_for_no_payment_pod

before="$(stock)"
reservation="$(curl --fail-with-body -sS -X POST "${BASE_URL}/reservations" \
  -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"${EVENT_ID}\",\"customerRef\":\"tp6-ttl@esgi.fr\",\"quantity\":2}")"
reservation_id="$(printf '%s' "${reservation}" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
test -n "${reservation_id}"
reservation_ttl="$(kubectl -n "${NAMESPACE}" exec deployment/booking-db -- \
  psql -v ON_ERROR_STOP=1 -U booking -d bookingdb -At \
  -c "SELECT round(extract(epoch FROM expires_at - created_at)) FROM reservations WHERE id = '${reservation_id}';")"
test "${reservation_ttl}" = "5"
echo "   réservation ${reservation_id}, stock ${before} -> $((before - 2))"

echo "2. Attente de ReservationExpired et SeatReleased"
current=""
for _ in $(seq 1 20); do
  current="$(curl -sS "${BASE_URL}/reservations/${reservation_id}" || true)"
  if printf '%s' "${current}" | grep -q '"status":"EXPIRED"'; then
    break
  fi
  sleep 1
done
printf '%s' "${current}" | grep -q '"status":"EXPIRED"'
test "$(stock)" = "${before}"
echo "   statut=EXPIRED, stock restauré à ${before}"

expired_events="$(kubectl -n "${NAMESPACE}" exec deployment/kafka -- \
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:29092 \
  --topic booking.reservation-expired --from-beginning --timeout-ms 5000 2>/dev/null || true)"
released_events="$(kubectl -n "${NAMESPACE}" exec deployment/kafka -- \
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:29092 \
  --topic booking.seat-released --from-beginning --timeout-ms 5000 2>/dev/null || true)"
test "$(printf '%s' "${expired_events}" | grep -c "${reservation_id}" || true)" = "1"
test "$(printf '%s' "${released_events}" | grep -c "${reservation_id}" || true)" = "1"

echo "3. Redémarrage du paiement : la réservation expirée n'est pas encaissée"
kubectl -n "${NAMESPACE}" scale deployment/payment-service --replicas=1 >/dev/null
kubectl -n "${NAMESPACE}" rollout status deployment/payment-service --timeout=180s >/dev/null
payment_status=""
for _ in $(seq 1 30); do
  payment_status="$(curl -sS \
    "${BASE_URL}/reservations/${reservation_id}/payment-status" || true)"
  if printf '%s' "${payment_status}" | grep -q 'REJECTED'; then
    break
  fi
  sleep 1
done
printf '%s' "${payment_status}" | grep -q 'REJECTED'
current="$(curl --fail-with-body -sS "${BASE_URL}/reservations/${reservation_id}")"
printf '%s' "${current}" | grep -q '"status":"EXPIRED"'
test "$(stock)" = "${before}"
echo "   paiement=REJECTED (RESERVATION_EXPIRED), aucune double libération"

restore
trap - EXIT
echo "TP06 expiration TTL : OK (configuration restaurée à ${original_ttl}s)"
