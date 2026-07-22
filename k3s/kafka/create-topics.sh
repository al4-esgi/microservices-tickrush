#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVER:-kafka:29092}"
KAFKA_BIN="/opt/kafka/bin"

topics=(
  booking.seat-reserved
  booking.reservation-expired
  booking.seat-released
  booking.ticket-issued
  payment.received
  payment.rejected
  booking.seat-reserved.DLQ
  payment.received.DLT
)

echo "Waiting for Kafka at ${BOOTSTRAP_SERVER}..."
until "${KAFKA_BIN}/kafka-topics.sh" \
  --bootstrap-server "${BOOTSTRAP_SERVER}" --list >/dev/null 2>&1; do
  sleep 2
done

for topic in "${topics[@]}"; do
  "${KAFKA_BIN}/kafka-topics.sh" \
    --bootstrap-server "${BOOTSTRAP_SERVER}" \
    --create \
    --if-not-exists \
    --topic "${topic}" \
    --partitions 3 \
    --replication-factor 1
done

echo "TickRush topics:"
"${KAFKA_BIN}/kafka-topics.sh" \
  --bootstrap-server "${BOOTSTRAP_SERVER}" --list | sort
