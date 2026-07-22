#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
NAMESPACE="${NAMESPACE:-tickrush}"
EVENT_ID="${EVENT_ID:-11111111-1111-1111-1111-111111111111}"

stock() {
  curl --fail-with-body -sS "${BASE_URL}/events/${EVENT_ID}" | \
    sed -n 's/.*"availableSeats":\([0-9]*\).*/\1/p'
}

wait_for_status() {
  local reservation_id="$1"
  local expected="$2"
  local current=""
  for _ in $(seq 1 30); do
    current="$(curl --fail-with-body -sS "${BASE_URL}/reservations/${reservation_id}")"
    if printf '%s' "${current}" | grep -q "\"status\":\"${expected}\""; then
      printf '%s' "${current}"
      return 0
    fi
    sleep 1
  done
  printf '%s\n' "${current}" >&2
  return 1
}

read_topic() {
  local topic="$1"
  kubectl -n "${NAMESPACE}" exec deployment/kafka -- \
    /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server kafka:29092 \
    --topic "${topic}" \
    --from-beginning \
    --timeout-ms 5000 2>/dev/null || true
}

echo "1. Chemin nominal : SeatReserved -> PaymentReceived -> TicketIssued"
nominal_before="$(stock)"
nominal="$(curl --fail-with-body -sS -X POST "${BASE_URL}/reservations" \
  -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"${EVENT_ID}\",\"customerRef\":\"tp6-nominal@esgi.fr\",\"quantity\":1}")"
nominal_id="$(printf '%s' "${nominal}" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
test -n "${nominal_id}"
nominal_final="$(wait_for_status "${nominal_id}" TICKET_ISSUED)"
printf '%s' "${nominal_final}" | grep -Eq '"ticketId":"[0-9a-f-]{36}"'
nominal_after="$(stock)"
test "${nominal_after}" -eq $((nominal_before - 1))
ticket_events="$(read_topic booking.ticket-issued)"
test "$(printf '%s' "${ticket_events}" | grep -c "${nominal_id}" || true)" = "1"
echo "   statut=TICKET_ISSUED, stock ${nominal_before} -> ${nominal_after}, TicketIssued=1"

for _ in $(seq 1 20); do
  if kubectl -n "${NAMESPACE}" logs deployment/notification-service --since=60s | \
      grep -q "Email TicketIssued envoyé: reservationId=${nominal_id}"; then
    echo "   email de billet capturé par MailDev"
    break
  fi
  sleep 1
done
kubectl -n "${NAMESPACE}" logs deployment/notification-service --since=90s | \
  grep "Email TicketIssued envoyé: reservationId=${nominal_id}" | tail -1

echo "2. Chemin d'échec : PaymentFailed -> CANCELLED -> SeatReleased"
failure_before="$(stock)"
failed="$(curl --fail-with-body -sS -X POST "${BASE_URL}/reservations" \
  -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"${EVENT_ID}\",\"customerRef\":\"tp6-failure@esgi.fr\",\"quantity\":3}")"
failed_id="$(printf '%s' "${failed}" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
test -n "${failed_id}"
failed_final="$(wait_for_status "${failed_id}" CANCELLED)"
printf '%s' "${failed_final}" | grep -q '"amount":149.70'
failure_after="$(stock)"
test "${failure_after}" = "${failure_before}"
released_events="$(read_topic booking.seat-released)"
test "$(printf '%s' "${released_events}" | grep -c "${failed_id}" || true)" = "1"
echo "   statut=CANCELLED, stock ${failure_before} -> ${failure_after}, SeatReleased=1"

echo "3. Rejeu du même PaymentFailed : aucune seconde compensation"
payment_row="$(kubectl -n "${NAMESPACE}" exec deployment/payment-db -- \
  psql -v ON_ERROR_STOP=1 -U payment -d paymentdb -At -F '|' \
  -c "SELECT id, amount FROM payments WHERE \"reservationId\" = '${failed_id}';")"
payment_id="${payment_row%%|*}"
amount="${payment_row#*|}"
test -n "${payment_id}"
occurred_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
duplicate="$(printf \
  '{"eventId":"%s","eventType":"PaymentFailed","occurredAt":"%s","aggregateId":"%s","payload":{"paymentId":"%s","reservationId":"%s","amount":%s,"reason":"AMOUNT_THRESHOLD"}}' \
  "${payment_id}" "${occurred_at}" "${failed_id}" "${payment_id}" "${failed_id}" "${amount}")"
printf '%s:%s\n' "${failed_id}" "${duplicate}" | \
  kubectl -n "${NAMESPACE}" exec -i deployment/kafka -- \
    /opt/kafka/bin/kafka-console-producer.sh \
    --bootstrap-server kafka:29092 \
    --topic payment.rejected \
    --property parse.key=true \
    --property key.separator=: >/dev/null
sleep 3

marker_count="$(kubectl -n "${NAMESPACE}" exec deployment/booking-db -- \
  psql -v ON_ERROR_STOP=1 -U booking -d bookingdb -At \
  -c "SELECT count(*) FROM processed_events WHERE event_id = '${payment_id}';")"
test "${marker_count}" = "1"
test "$(stock)" = "${failure_after}"
released_after_replay="$(read_topic booking.seat-released)"
test "$(printf '%s' "${released_after_replay}" | grep -c "${failed_id}" || true)" = "1"
kubectl -n "${NAMESPACE}" logs deployment/booking-service --since=30s | \
  grep "Evenement deja traite: eventId=${payment_id}" | tail -1
echo "   processed_events=1, stock inchangé, SeatReleased toujours unique"

echo "TP06 saga nominale + compensation idempotente : OK"
