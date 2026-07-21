# payment-service

Service de paiement simulé de TickRush, développé avec NestJS et TypeORM. Il possède sa
propre base PostgreSQL et garantit un paiement unique par réservation grâce à la contrainte
`UNIQUE(reservationId)`.

## Contrats HTTP

- `POST /payments` : crée un paiement (`201`) ou renvoie le paiement existant (`200`).
- `GET /payments/by-reservation/{reservationId}/status` : `RECEIVED`, `REJECTED` ou `NONE`.
- `GET /health/live` : santé du processus.
- `GET /health/ready` : santé du processus et connexion PostgreSQL.

Le taux de refus est configurable avec `PAYMENT_FAILURE_RATE` entre 0 et 1. L'idempotence
reste garantie sous concurrence : une violation PostgreSQL `23505` provoque la relecture du
paiement créé par la requête gagnante.

## Développement

```bash
npm ci
npm run lint:check
npm test -- --runInBand
npm run test:e2e -- --runInBand
npm run build
```

En k3s, le service utilise `payment-db:5432`; aucun autre service n'accède à cette base.
