# TP05 - Pipeline événementiel polyglotte

Le sujet Docker Compose est adapté au cluster k3s validé par le formateur. Les applications
utilisent le listener interne `kafka:29092`; toutes les commandes de preuve passent par
`kubectl` ou le `Taskfile.yml`.

## Flux réalisé

```text
POST /reservations
  -> transaction stock + réservation PENDING
  -> booking.seat-reserved (SeatReserved, clé = reservationId)
  -> payment-service crée un paiement idempotent
  -> payment.received (PaymentReceived, même clé)
  -> booking-service insère processed_events et passe la réservation à PAID (frontière TP05)
```

Le concert seedé coûte `49.90 EUR` par place. `SeatReserved.payload` contient `quantity`,
`unitPrice` et le montant calculé avec `amount = unitPrice × quantity`. Une réservation à une
place produit donc un paiement de `49.90 EUR`.

La publication Java intervient avec un `TransactionalEventListener(AFTER_COMMIT)`. Cela évite
d'émettre une réservation annulée par un conflit de verrou optimiste. Il reste toutefois une
fenêtre entre le commit PostgreSQL et l'envoi Kafka : c'est le dual-write volontaire du TP05,
qui sera remplacé par l'Outbox au TP07.

## Enveloppe et clés

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "SeatReserved",
  "occurredAt": "2026-07-22T08:00:00Z",
  "aggregateId": "65bfbf4f-6795-49a5-a57b-6f4ff78f0ac1",
  "payload": {
    "reservationId": "65bfbf4f-6795-49a5-a57b-6f4ff78f0ac1",
    "eventId": "11111111-1111-1111-1111-111111111111",
    "customerRef": "client@esgi.fr",
    "quantity": 1,
    "unitPrice": 49.90,
    "amount": 49.90,
    "expiresAt": "2026-07-22T08:02:00Z"
  }
}
```

`aggregateId` et la clé Kafka valent tous deux `reservationId`. L'`eventId` de l'enveloppe
identifie le fait et sert à la déduplication; l'`eventId` du payload identifie l'événement de
billetterie.

## Idempotence transactionnelle

Le listener Java vérifie puis insère l'`eventId` dans `processed_events` avant de passer la
réservation à `PAID`. Le marqueur et l'effet métier sont exécutés dans la même transaction :
une erreur annule les deux. Rejouer le même `PaymentReceived` renvoie le log
`Evenement deja traite` et laisse un seul marqueur.

```bash
task tp5:demo
```

Cette commande crée une réservation, attend son passage à `PAID`, reconstruit le message
`PaymentReceived` avec le même `eventId`, le republie et vérifie en PostgreSQL que
`processed_events = 1`.

## Retries et dead-letter topics

- Node : boucle bornée à 3 traitements, backoff de 1 seconde, compteur
  `x-retry-count`, puis `booking.seat-reserved.DLQ`.
- Java : `DefaultErrorHandler`, tentative initiale + 2 retries, puis
  `payment.received.DLT` sur la même partition.
- Les deux topics morts sont créés explicitement avec 3 partitions, car l'auto-création Kafka
  est désactivée.

```bash
task tp5:poison
```

Le script injecte un JSON invalide de chaque côté, compte exactement trois échecs dans les
logs et retrouve les valeurs dans les topics morts. Le retour normal après republication
permet ensuite au consumer de traiter les offsets suivants.

Le script publie ensuite un événement valide portant la même clé de chaque côté et vérifie
que le lag des groupes `payment-service` et `booking-service` revient à zéro.

## Échec métier contrôlé

```bash
task tp5:rejection
```

Trois places au tarif de `49.90 EUR` produisent un montant de `149.70 EUR`, supérieur au
seuil `PAYMENT_REJECTION_THRESHOLD=100`. Le service Node persiste `REJECTED` et publie
`PaymentFailed` sur le topic `payment.rejected`. À la frontière du TP05, la réservation
restait volontairement `PENDING` : le TP06 ajoute la réaction, la remise des trois places en
stock et l'événement de compensation.

## Limites volontaires

- `PaymentFailed` est produit de façon déterministe pour un montant supérieur à
  `100 EUR`, mais sa compensation (remise des places en stock) appartient au TP06.
- L'émission du billet, le TTL automatique et les notifications Kafka seront raccordés à la
  saga au TP06.
- La fiabilisation du dual-write par Transactional Outbox appartient au TP07.

## Résultats observés dans k3s

- Les 8 topics existent avec 3 partitions et les pods applicatifs sont `Running` sans restart.
- Une réservation à `49.90 EUR` passe de `PENDING` à `PAID`; le paiement persistant vaut
  `RECEIVED`.
- Le rejeu du même `PaymentReceived` laisse exactement une ligne dans `processed_events` et
  produit le log `Evenement deja traite`.
- Un poison côté Node et un poison côté Java produisent chacun exactement 3 erreurs, arrivent
  dans leur DLQ/DLT, puis les groupes retrouvent tous deux un lag total de 0 après le message
  valide suivant.
- Une réservation de 3 places calcule `149.70 EUR` et publie `PaymentFailed`; elle restait
  `PENDING` conformément à la frontière entre TP05 et TP06.
