# TP08 - Observabilité

## Adaptation k3s validée

Le sujet utilise Docker Compose comme support d'exécution. TickRush reste sur l'option k3s
validée par le formateur : Prometheus, Grafana et Jaeger sont des Deployments du namespace
`tickrush`. Les fichiers de configuration, la datasource et le dashboard sont injectés par
[`k3s/observability/kustomization.yaml`](../k3s/observability/kustomization.yaml).

```bash
kubectl apply -k k3s/observability/
task forward
```

| Composant | Endpoint interne | Endpoint local |
|---|---|---|
| métriques Java | `booking-service:8080/actuator/prometheus` | `localhost:8080/actuator/prometheus` |
| métriques Node | `payment-service:3000/metrics` | `localhost:3000/metrics` |
| Prometheus | `prometheus:9090` | `localhost:9090` |
| Grafana | `grafana:3000` | `localhost:3001` |
| Jaeger UI | `jaeger:16686` | `localhost:16686` |
| Jaeger OTLP HTTP | `jaeger:4318` | interne au cluster |

## Métriques et RED

Spring Boot expose les métriques HTTP, JVM et Kafka avec Actuator, Micrometer et le registre
Prometheus. L'histogramme HTTP est activé afin que le p95 soit calculable. NestJS utilise un
registre `prom-client`, les métriques processus par défaut et un interceptor global qui
observe méthode, route normalisée, statut et durée.

Le dashboard provisionné contient :

| Panneau | Série principale |
|---|---|
| Rate | `rate(http_server_requests_seconds_count[...])` et équivalent Node |
| Errors | mêmes compteurs filtrés sur `status=~"5.."` |
| Duration p95 | `histogram_quantile(0.95, rate(..._bucket[...]))` |
| Consumer lag | `kafka_consumer_fetch_manager_records_lag_max` |

Le dashboard est exporté à l'emplacement demandé par le sujet dans
[`monitoring/grafana/tickrush-red.json`](../monitoring/grafana/tickrush-red.json). Sa copie
injectée par Kustomize se trouve sous `k3s/observability/grafana/dashboards/`; le script de
démonstration exige que les deux fichiers soient identiques. La datasource Prometheus et le
provider de dashboards sont eux aussi versionnés. Le script TP8 redémarre Grafana avant de
rechercher le dashboard par son UID `tickrush-red-kafka`, ce qui prouve son provisionnement.

## Trace distribuée et Outbox

Le Java agent `2.28.1` instrumente Spring MVC, JDBC, Hibernate et Spring Kafka. Node charge
`@opentelemetry/auto-instrumentations-node` avec `NODE_OPTIONS`, avant NestJS, TypeORM,
KafkaJS et Pino. Les deux exportent en OTLP HTTP vers Jaeger v2.

L'Outbox impose une propagation supplémentaire : le span HTTP ou consumer est terminé quand
le relayeur asynchrone publie plus tard. Chaque ligne Outbox stocke donc le contexte W3C :

- `trace_parent` : identifiant de trace, span parent et flags;
- `trace_state` : informations fournisseur éventuelles;
- `baggage` : métadonnées de corrélation propagées.

Le relayeur restaure ce contexte et le place dans les headers Kafka avant l'envoi. Le span
producteur créé par l'agent devient un descendant de l'opération d'origine; le consommateur
suivant rejoint la même trace. Cette logique est appliquée aux Outbox booking et payment.

La trace compensée validée contient notamment :

```text
booking-service  POST /reservations
booking-service  booking.seat-reserved publish
payment-service  process booking.seat-reserved
payment-service  send payment.rejected
booking-service  payment.rejected process
booking-service  booking.seat-released publish
```

## Logs corrélés

`booking-service` utilise `logstash-logback-encoder`; `payment-service` utilise
`nestjs-pino`. Les instrumentations Logback MDC et Pino ajoutent automatiquement
`trace_id`, `span_id` et les flags aux logs produits sous un span actif.

```bash
kubectl -n tickrush logs deployment/booking-service | grep '"trace_id"' | tail -1
kubectl -n tickrush logs deployment/payment-service | grep '"trace_id"' | tail -1
```

Le script extrait les lignes contenant le même `reservationId` dans les deux services et
exige des `trace_id` identiques avant d'interroger Jaeger.

## Preuve automatisée

```bash
task tp8:demo
```

Le scénario vérifie successivement :

1. disponibilité des cinq Deployments utiles;
2. métriques Java/Node et targets Prometheus `UP`;
3. dashboard présent après redémarrage Grafana;
4. saga nominale `TICKET_ISSUED` et saga refusée `CANCELLED`;
5. logs JSON corrélés dans les deux langages;
6. trace Jaeger réunissant les deux services et la compensation;
7. séries Rate, histogramme HTTP et lag Kafka présentes dans Prometheus.

L'exécution de référence a retourné :

```text
trace_id=8fb045545edc2e3a0933586cb1425ece
services=booking-service,payment-service, spans=57
TP08 observabilité : métriques, dashboard, trace distribuée et logs corrélés : OK
```

## Choix soutenance

La démonstration principale est la trace Jaeger du chemin d'échec. Elle concentre les points
les plus discriminants du projet : polyglottisme, Kafka, Outbox, causalité distribuée et
compensation. Le dashboard reste ouvert dans un second onglet pour partir d'un signal RED ou
du lag, puis la trace localise le parcours et les logs expliquent l'événement métier.
