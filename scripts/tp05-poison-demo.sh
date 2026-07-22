#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-tickrush}"
stamp="$(date +%s)"
node_key="$(uuidgen | tr '[:upper:]' '[:lower:]')"
java_key="$(uuidgen | tr '[:upper:]' '[:lower:]')"
node_poison="NOT_JSON_NODE_TP5_${stamp}"
java_poison="NOT_JSON_JAVA_TP5_${stamp}"

produce_poison() {
  local topic="$1"
  local key="$2"
  local value="$3"
  printf '%s:%s\n' "${key}" "${value}" | \
    kubectl -n "${NAMESPACE}" exec -i deployment/kafka -- \
      /opt/kafka/bin/kafka-console-producer.sh \
      --bootstrap-server kafka:29092 \
      --topic "${topic}" \
      --property parse.key=true \
      --property key.separator=: >/dev/null
}

read_dead_letter() {
  local topic="$1"
  kubectl -n "${NAMESPACE}" exec deployment/kafka -- \
    /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server kafka:29092 \
    --topic "${topic}" \
    --from-beginning \
    --timeout-ms 10000 \
    --property print.key=true \
    --property key.separator=' | ' 2>/dev/null || true
}

echo "1. Poison pill côté Node"
produce_poison booking.seat-reserved "${node_key}" "${node_poison}"

echo "2. Poison pill côté Java"
produce_poison payment.received "${java_key}" "${java_poison}"

sleep 8

node_attempts="$(kubectl -n "${NAMESPACE}" logs deployment/payment-service --since=30s | \
  grep -c "key=${node_key}" || true)"
java_attempts="$(kubectl -n "${NAMESPACE}" logs deployment/booking-service --since=30s | \
  grep -c "key=${java_key}" || true)"
test "${node_attempts}" = "3"
test "${java_attempts}" = "3"
echo "   Node=${node_attempts} tentatives, Java=${java_attempts} tentatives"

node_dlq="$(read_dead_letter booking.seat-reserved.DLQ)"
java_dlt="$(read_dead_letter payment.received.DLT)"
printf '%s' "${node_dlq}" | grep -q "${node_poison}"
printf '%s' "${java_dlt}" | grep -q "${java_poison}"
echo "   messages retrouvés dans booking.seat-reserved.DLQ et payment.received.DLT"

echo "3. Messages valides après les poisons"
occurred_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
node_probe_id="$(uuidgen | tr '[:upper:]' '[:lower:]')"
java_probe_id="$(uuidgen | tr '[:upper:]' '[:lower:]')"
node_probe="$(printf \
  '{"eventId":"%s","eventType":"PartitionProbe","occurredAt":"%s","aggregateId":"%s","payload":{}}' \
  "${node_probe_id}" "${occurred_at}" "${node_key}")"
java_probe="$(printf \
  '{"eventId":"%s","eventType":"FuturePaymentEvent","occurredAt":"%s","aggregateId":"%s","payload":{}}' \
  "${java_probe_id}" "${occurred_at}" "${java_key}")"
produce_poison booking.seat-reserved "${node_key}" "${node_probe}"
produce_poison payment.received "${java_key}" "${java_probe}"
sleep 5

node_lag="$(kubectl -n "${NAMESPACE}" exec deployment/kafka -- \
  /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:29092 --describe --group payment-service 2>/dev/null | \
  awk 'NR > 1 && $6 ~ /^[0-9]+$/ { total += $6 } END { print total + 0 }')"
java_lag="$(kubectl -n "${NAMESPACE}" exec deployment/kafka -- \
  /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:29092 --describe --group booking-service 2>/dev/null | \
  awk 'NR > 1 && $6 ~ /^[0-9]+$/ { total += $6 } END { print total + 0 }')"
test "${node_lag}" = "0"
test "${java_lag}" = "0"
echo "   lag payment-service=${node_lag}, lag booking-service=${java_lag}"

echo "TP05 poison pills: OK, les offsets suivants sont traités"
