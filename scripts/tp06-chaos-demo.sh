#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
NAMESPACE="${NAMESPACE:-tickrush}"
EVENT_ID="${EVENT_ID:-11111111-1111-1111-1111-111111111111}"

original_replicas="$(kubectl -n "${NAMESPACE}" get deployment/payment-service -o jsonpath='{.spec.replicas}')"
original_replicas="${original_replicas:-1}"

restore() {
  set +e
  kubectl -n "${NAMESPACE}" scale deployment/payment-service \
    --replicas="${original_replicas}" >/dev/null
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

echo "1. Arrêt de payment-service"
kubectl -n "${NAMESPACE}" scale deployment/payment-service --replicas=0 >/dev/null
kubectl -n "${NAMESPACE}" rollout status deployment/payment-service --timeout=60s >/dev/null
wait_for_no_payment_pod

echo "2. Création d'une réservation pendant l'indisponibilité"
reservation="$(curl --fail-with-body -sS -X POST "${BASE_URL}/reservations" \
  -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"${EVENT_ID}\",\"customerRef\":\"tp6-chaos@esgi.fr\",\"quantity\":1}")"
reservation_id="$(printf '%s' "${reservation}" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
test -n "${reservation_id}"
sleep 2
pending="$(curl --fail-with-body -sS "${BASE_URL}/reservations/${reservation_id}")"
printf '%s' "${pending}" | grep -q '"status":"PENDING"'
echo "   réservation PENDING, SeatReserved attend dans Kafka"

echo "3. Redémarrage : la saga reprend sans nouvel appel client"
kubectl -n "${NAMESPACE}" scale deployment/payment-service --replicas=1 >/dev/null
kubectl -n "${NAMESPACE}" rollout status deployment/payment-service --timeout=180s >/dev/null
current=""
for _ in $(seq 1 30); do
  current="$(curl -sS "${BASE_URL}/reservations/${reservation_id}" || true)"
  if printf '%s' "${current}" | grep -q '"status":"TICKET_ISSUED"'; then
    break
  fi
  sleep 1
done
printf '%s' "${current}" | grep -q '"status":"TICKET_ISSUED"'
printf '%s' "${current}" | grep -Eq '"ticketId":"[0-9a-f-]{36}"'
echo "   statut=TICKET_ISSUED : rupture du couplage temporel prouvée"

restore
trap - EXIT
echo "TP06 chaos : OK"
