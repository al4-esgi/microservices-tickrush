# payment-service

Service de paiement simulé de TickRush, développé avec NestJS et TypeORM. Il possède sa
propre base PostgreSQL et garantit un paiement unique par réservation grâce à la contrainte
`UNIQUE(reservationId)`.

## Contrats HTTP

- `POST /payments` : crée un paiement (`201`) ou renvoie le paiement existant (`200`).
- `GET /payments/by-reservation/{reservationId}/status` : `RECEIVED`, `REJECTED` ou `NONE`.
- `GET /health/live` : santé du processus.
- `GET /health/ready` : santé du processus et connexion PostgreSQL.
- `GET /metrics` : métriques processus et histogramme HTTP Prometheus.

Le taux de refus est configurable avec `PAYMENT_FAILURE_RATE` entre 0 et 1. L'idempotence
reste garantie sous concurrence : une violation PostgreSQL `23505` provoque la relecture du
paiement créé par la requête gagnante.

Depuis le TP05, le service consomme `booking.seat-reserved` avec KafkaJS et appelle la même
logique métier que l'endpoint REST. Depuis le TP07, le paiement et `PaymentReceived` ou
`PaymentFailed` sont écrits dans la même transaction TypeORM. Le relayeur Outbox publie
ensuite sur `payment.received` ou `payment.rejected`, avec la clé `reservationId`, et ne
renseigne `published_at` qu'après l'ACK Kafka.
`PAYMENT_REJECTION_THRESHOLD` vaut `100` dans k3s : une place à
`49.90 EUR` est acceptée, trois places à `149.70 EUR` sont refusées de façon déterministe.

Un message invalide est traité trois fois avec backoff puis copié, avec ses headers d'erreur,
dans `booking.seat-reserved.DLQ`. Le consumer retourne ensuite normalement afin de ne pas
bloquer la partition.

Le payload `SeatReserved` contient aussi `expiresAt`. Un message traité après cette échéance
est persisté `REJECTED` avec la raison `RESERVATION_EXPIRED`; aucune réservation expirée
n'est encaissée lors de la reprise après une panne.

## Développement

```bash
npm ci
npm run lint:check
npm test -- --runInBand
npm run test:e2e -- --runInBand
npm run build
```

En k3s, le service utilise `payment-db:5432`; aucun autre service n'accède à cette base.
La table `outbox` est privée au service, comme la table `payments`.

## Observabilité

`prom-client` expose les métriques par défaut et
`tickrush_http_request_duration_seconds{method,route,status}`. `nestjs-pino` produit les logs
JSON. En k3s, `NODE_OPTIONS=--require @opentelemetry/auto-instrumentations-node/register`
charge OpenTelemetry avant NestJS, TypeORM et KafkaJS; Pino ajoute alors `trace_id` et
`span_id` aux logs actifs.

Le contexte W3C reçu avec `SeatReserved` est stocké dans la ligne Outbox du résultat de
paiement, puis restauré par le relayeur. `PaymentReceived` ou `PaymentFailed` reste ainsi dans
la trace initiée par `POST /reservations`, malgré le polling asynchrone.
