#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
NAMESPACE="${NAMESPACE:-tickrush}"
EVENT_ID="${EVENT_ID:-11111111-1111-1111-1111-111111111111}"
BOOKING_METRICS_URL="http://localhost:${BOOKING_METRICS_PORT:-18080}"
PAYMENT_METRICS_URL="http://localhost:${PAYMENT_METRICS_PORT:-13000}"
PROMETHEUS_URL="http://localhost:${PROMETHEUS_PORT:-19090}"
GRAFANA_URL="http://localhost:${GRAFANA_PORT:-13001}"
JAEGER_URL="http://localhost:${JAEGER_PORT:-16687}"
forward_pids=()
forward_dir="$(mktemp -d /tmp/tickrush-tp8.XXXXXX)"

cleanup() {
  for pid in "${forward_pids[@]:-}"; do
    kill "${pid}" 2>/dev/null || true
    wait "${pid}" 2>/dev/null || true
  done
  rm -rf "${forward_dir}"
}
trap cleanup EXIT INT TERM

start_forward() {
  local service="$1"
  local mapping="$2"
  kubectl -n "${NAMESPACE}" port-forward "svc/${service}" "${mapping}" \
    >"${forward_dir}/${service}.log" 2>&1 &
  forward_pids+=("$!")
}

wait_http() {
  local url="$1"
  for _ in $(seq 1 40); do
    if curl --max-time 2 --fail -sS "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "Endpoint indisponible: ${url}" >&2
  return 1
}

wait_for_status() {
  local reservation_id="$1"
  local expected="$2"
  local current=""
  for _ in $(seq 1 60); do
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

create_reservation() {
  local quantity="$1"
  local customer="$2"
  local response
  response="$(curl --fail-with-body -sS -X POST "${BASE_URL}/reservations" \
    -H 'Content-Type: application/json' \
    -d "{\"eventId\":\"${EVENT_ID}\",\"customerRef\":\"${customer}\",\"quantity\":${quantity}}")"
  printf '%s' "${response}" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p'
}

trace_id_from_logs() {
  local deployment="$1"
  local reservation_id="$2"
  kubectl -n "${NAMESPACE}" logs "deployment/${deployment}" --since=5m | \
    grep "${reservation_id}" | \
    sed -n 's/.*"trace_id":"\([0-9a-f]\{32\}\)".*/\1/p' | \
    tail -1 || true
}

command -v jq >/dev/null
cmp -s monitoring/grafana/tickrush-red.json \
  k3s/observability/grafana/dashboards/tickrush-red.json || {
  echo "L'export Grafana et la copie provisionnée k3s divergent" >&2
  exit 1
}

echo "0. Préconditions k3s et redémarrage de Grafana"
for deployment in booking-service payment-service prometheus grafana jaeger; do
  kubectl -n "${NAMESPACE}" rollout status "deployment/${deployment}" --timeout=240s >/dev/null
done
kubectl -n "${NAMESPACE}" rollout restart deployment/grafana >/dev/null
kubectl -n "${NAMESPACE}" rollout status deployment/grafana --timeout=240s >/dev/null

start_forward booking-service "${BOOKING_METRICS_URL##*:}:8080"
start_forward payment-service "${PAYMENT_METRICS_URL##*:}:3000"
start_forward prometheus "${PROMETHEUS_URL##*:}:9090"
start_forward grafana "${GRAFANA_URL##*:}:3000"
start_forward jaeger "${JAEGER_URL##*:}:16686"
wait_http "${BOOKING_METRICS_URL}/actuator/health"
wait_http "${PAYMENT_METRICS_URL}/health/live"
wait_http "${PROMETHEUS_URL}/-/ready"
wait_http "${GRAFANA_URL}/api/health"
wait_http "${JAEGER_URL}/"
echo "   services et outils d'observabilité prêts"

echo "1. Endpoints de métriques et cibles Prometheus"
booking_metrics="$(curl --fail -sS "${BOOKING_METRICS_URL}/actuator/prometheus")"
payment_metrics="$(curl --fail -sS "${PAYMENT_METRICS_URL}/metrics")"
grep -q '^jvm_memory_used_bytes' <<<"${booking_metrics}"
grep -q '^http_server_requests_seconds_count' <<<"${booking_metrics}"
grep -q '^payment_service_process_cpu_seconds_total' <<<"${payment_metrics}"
grep -q '^tickrush_http_request_duration_seconds_count' <<<"${payment_metrics}"

for _ in $(seq 1 20); do
  targets="$(curl --fail -sS "${PROMETHEUS_URL}/api/v1/targets")"
  booking_up="$(printf '%s' "${targets}" | jq '[.data.activeTargets[] | select(.labels.job == "booking-service" and .health == "up")] | length')"
  payment_up="$(printf '%s' "${targets}" | jq '[.data.activeTargets[] | select(.labels.job == "payment-service" and .health == "up")] | length')"
  if test "${booking_up}" = "1" && test "${payment_up}" = "1"; then
    break
  fi
  sleep 2
done
test "${booking_up}" = "1"
test "${payment_up}" = "1"
echo "   booking-service=UP, payment-service=UP"

echo "2. Dashboard RED + lag provisionné après redémarrage"
dashboard=""
for _ in $(seq 1 30); do
  dashboard="$(curl -sS -u admin:admin \
    "${GRAFANA_URL}/api/dashboards/uid/tickrush-red-kafka")"
  if printf '%s' "${dashboard}" | jq -e \
      '.dashboard.title == "TickRush - RED et Kafka"' >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
test "$(printf '%s' "${dashboard}" | jq -r '.dashboard.title')" = "TickRush - RED et Kafka"
test "$(printf '%s' "${dashboard}" | jq '.dashboard.panels | length')" -ge 4
printf '%s' "${dashboard}" | jq -e \
  '[.dashboard.panels[].title] | all(.[]; test("Rate|Errors|Duration|lag"; "i"))' >/dev/null
echo "   dashboard retrouvé par API avec 4 panneaux"

echo "3. Génération des traces nominale et compensée"
nominal_id="$(create_reservation 1 tp8-nominal@esgi.fr)"
test -n "${nominal_id}"
wait_for_status "${nominal_id}" TICKET_ISSUED >/dev/null
failed_id="$(create_reservation 3 tp8-compensation@esgi.fr)"
test -n "${failed_id}"
wait_for_status "${failed_id}" CANCELLED >/dev/null
echo "   nominal=${nominal_id} -> TICKET_ISSUED"
echo "   échec=${failed_id} -> CANCELLED -> SeatReleased"

echo "4. Logs JSON corrélés des deux services"
booking_trace_id=""
payment_trace_id=""
for _ in $(seq 1 30); do
  booking_trace_id="$(trace_id_from_logs booking-service "${failed_id}")"
  payment_trace_id="$(trace_id_from_logs payment-service "${failed_id}")"
  if test -n "${booking_trace_id}" && test -n "${payment_trace_id}"; then
    break
  fi
  sleep 1
done
test "${booking_trace_id}" = "${payment_trace_id}"
test "${#booking_trace_id}" = "32"
echo "   trace_id=${booking_trace_id} présent dans Logback JSON et Pino"

echo "5. Trace Jaeger complète, Kafka et compensation comprises"
trace=""
trace_complete=false
for _ in $(seq 1 40); do
  trace="$(curl -sS "${JAEGER_URL}/api/traces/${booking_trace_id}" || printf '{"data":[]}')"
  if printf '%s' "${trace}" | jq -e '
      (.data | length) >= 1 and
      ([.data[0].processes[].serviceName] | index("booking-service") != null) and
      ([.data[0].processes[].serviceName] | index("payment-service") != null) and
      ([.data[0].spans[].operationName] as $operations |
        ($operations | index("booking.seat-reserved publish") != null) and
        ($operations | index("process booking.seat-reserved") != null) and
        ($operations | index("send payment.rejected") != null) and
        ($operations | index("payment.rejected process") != null) and
        ($operations | index("booking.seat-released publish") != null))
    ' >/dev/null 2>&1; then
    # L'exporteur OTLP envoie les spans par lots; attendre le dernier batch avant le bilan.
    sleep 6
    trace="$(curl --fail -sS "${JAEGER_URL}/api/traces/${booking_trace_id}")"
    trace_complete=true
    break
  fi
  sleep 2
done
"${trace_complete}"
services="$(printf '%s' "${trace}" | jq -r '[.data[0].processes[].serviceName] | unique | sort | join(",")')"
grep -q 'booking-service' <<<"${services}"
grep -q 'payment-service' <<<"${services}"
span_names="$(printf '%s' "${trace}" | jq -r '.data[0].spans[].operationName')"
grep -Eiq 'payment\.rejected|booking\.seat-released|kafka' <<<"${span_names}"
printf '%s' "${booking_trace_id}" > /tmp/tickrush-tp8-trace-id
echo "   services=${services}, spans=$(printf '%s\n' "${span_names}" | wc -l | tr -d ' ')"

echo "6. Séries du dashboard alimentées"
sleep 6
rate_result="$(curl --fail -sS --get "${PROMETHEUS_URL}/api/v1/query" \
  --data-urlencode 'query=http_server_requests_seconds_count{job="booking-service"}')"
node_result="$(curl --fail -sS --get "${PROMETHEUS_URL}/api/v1/query" \
  --data-urlencode 'query=tickrush_http_request_duration_seconds_count{job="payment-service"}')"
lag_result="$(curl --fail -sS --get "${PROMETHEUS_URL}/api/v1/query" \
  --data-urlencode 'query=kafka_consumer_fetch_manager_records_lag_max{job="booking-service"}')"
test "$(printf '%s' "${rate_result}" | jq '.data.result | length')" -ge 1
test "$(printf '%s' "${node_result}" | jq '.data.result | length')" -ge 1
test "$(printf '%s' "${lag_result}" | jq '.data.result | length')" -ge 1
echo "   Rate, Duration/Errors et consumer lag disponibles dans Prometheus"

echo "TP08 observabilité : métriques, dashboard, trace distribuée et logs corrélés : OK"
