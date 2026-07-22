# Découpage du domaine — TickRush

Event Storming *light* du sujet **TickRush** (billetterie événementielle à stock limité).
Cette carte est la boussole des 8 prochaines séances : la table des contrats (§4) donne
les endpoints REST et les topics Kafka créés au TP4.

---

## 1. Événements (passe 1)

Événements métier au passé, dans l'ordre chronologique. Chemin nominal **et** chemins d'échec.

| #  | Événement            | Déclenché quand...                                                        |
|----|----------------------|--------------------------------------------------------------------------|
| 1  | ÉvénementOuvert      | un organisateur met en vente un événement avec un stock de places fixe   |
| 2  | PlacesRéservées      | un client réserve N places et le stock disponible est suffisant          |
| 3  | RéservationRefusée   | **(échec)** le stock restant est insuffisant pour les N places demandées |
| 4  | PaiementReçu         | le client paie sa réservation dans le délai imparti                      |
| 5  | PaiementRefusé       | **(échec)** le PSP simulé rejette le paiement                            |
| 6  | BilletÉmis           | le paiement est confirmé → un billet est généré pour la réservation      |
| 7  | RéservationExpirée   | **(échec)** le délai de paiement (TTL 2 min) s'écoule sans paiement       |
| 8  | PlacesLibérées       | une réservation expire ou est annulée → les places retournent au stock   |
| 9  | RéservationAnnulée   | le client annule sa réservation avant paiement                           |

> 9 événements, dont **3 d'échec** (RéservationRefusée, PaiementRefusé, RéservationExpirée).

---

## 2. Commandes (passe 2)

| Commande            | Acteur              | Événement(s) résultant(s)                |
|---------------------|---------------------|------------------------------------------|
| OuvrirÉvénement     | Organisateur        | ÉvénementOuvert                          |
| RéserverPlaces      | Client              | PlacesRéservées **OU** RéservationRefusée |
| PayerRéservation    | Client              | PaiementReçu **OU** PaiementRefusé        |
| ÉmettreBillet       | Système             | BilletÉmis                               |
| ExpirerRéservation  | Système (scheduler) | RéservationExpirée → PlacesLibérées       |
| AnnulerRéservation  | Client              | RéservationAnnulée → PlacesLibérées        |
| LibérerPlaces       | Système             | PlacesLibérées                           |

---

## 3. Agrégats et Bounded Contexts (passes 3 & 4)

### Contexte 1 — Réservation (`booking-service`, Java / Spring Boot)

- **Agrégats** :
  - `Événement` — porte le **stock** (places totales / disponibles). Cœur de la
    contrainte « ne jamais survendre » → protégé par **verrou optimiste**. Il définit aussi
    le prix unitaire courant d'une place.
  - `Réservation` — cycle de vie `PENDING → TICKET_ISSUED | EXPIRED | CANCELLED`, avec **TTL**.
    Elle fige le prix unitaire et le montant `unitPrice × quantity` au moment de la vente.
- **Événements émis** : PlacesRéservées, RéservationRefusée, RéservationExpirée,
  PlacesLibérées, BilletÉmis, RéservationAnnulée.
- **Événements consommés** : PaiementReçu, PaiementRefusé.

### Contexte 2 — Paiement (`payment-service`, Node / NestJS)

- **Agrégat** : `Paiement` — autorisation simulée (succès/échec configurable), **idempotent**
  (un même paiement rejoué n'émet pas deux fois).
- **Événements émis** : PaiementReçu, PaiementRefusé.
- **Événements consommés** : PlacesRéservées (sait quelle réservation encaisser).

### Contexte 3 — Notification (`notification-service`, Python / FastAPI) — *bonus*

- Consomme BilletÉmis / RéservationExpirée par Kafka → « email » simulé dans MailDev.

**Test de validation des frontières** : pour un cas d'usage (réserver → payer → billet),
Réservation↔Paiement échangent **1 appel synchrone** (statut du paiement) + des faits
asynchrones (Kafka). ≤ 3 interactions synchrones ⇒ frontière saine, on ne fusionne pas.

---

## 4. Contrats

### REST (synchrone — questions / actions)

| Service         | Endpoint                                  | Usage                                   |
|-----------------|-------------------------------------------|-----------------------------------------|
| booking-service | `GET /events/{id}`                        | consulter un événement et son stock     |
| booking-service | `POST /reservations`                      | réserver N places                       |
| booking-service | `GET /reservations/{id}`                  | suivre une réservation                  |
| booking-service | `GET /reservations/{id}/payment-status`   | statut de paiement (appel protégé, TP3) |
| payment-service | `POST /payments`                          | déclencher un paiement simulé           |
| payment-service | `GET /payments/by-reservation/{id}/status`| statut d'un paiement (cible du CB, TP3) |

### Enveloppe polyglotte (TP5)

Tous les messages utilisent le même contrat de transport :

```json
{
  "eventId": "UUID unique du fait",
  "eventType": "SeatReserved",
  "occurredAt": "2026-07-22T08:00:00Z",
  "aggregateId": "UUID de la réservation",
  "payload": {}
}
```

- `eventId` est la clé de déduplication du consommateur ;
- `eventType` permet d'ignorer sans erreur les futurs types inconnus ;
- `occurredAt` décrit l'instant métier indépendamment de Kafka ;
- `aggregateId` vaut ici `reservationId` et constitue aussi la **clé Kafka** ;
- `payload` porte les données propres au fait, dont l'`eventId` métier du concert ou match.

### Événements (asynchrone)

| Événement          | Topic                         | Clé Kafka       | Émetteur        | Consommateur(s) prévu(s)         |
|--------------------|-------------------------------|-----------------|-----------------|----------------------------------|
| PlacesRéservées    | `booking.seat-reserved`       | `reservationId` | booking-service | payment-service                  |
| RéservationExpirée | `booking.reservation-expired` | `reservationId` | booking-service | notification-service             |
| PlacesLibérées     | `booking.seat-released`       | `reservationId` | booking-service | audit / future projection        |
| BilletÉmis         | `booking.ticket-issued`       | `reservationId` | booking-service | notification-service             |
| PaiementReçu       | `payment.received`            | `reservationId` | payment-service | booking-service                  |
| PaiementRefusé     | `payment.rejected`            | `reservationId` | payment-service | booking-service                  |

La clé `reservationId`, égale à l'`aggregateId`, conserve l'ordre du cycle de vie de chaque
réservation et permet à `SeatReserved` puis `PaymentReceived` de rester corrélés. Le payload
de `SeatReserved` conserve séparément l'`eventId` métier, `quantity`, `unitPrice`, `amount` et
`expiresAt`. Tous les faits de la saga, y compris `SeatReleased`, restent partitionnés par
`reservationId` afin de conserver l'ordre du cycle de vie de cet agrégat.
Tous les topics ont 3 partitions et un facteur de réplication de 1 dans le cluster local
mono-broker du TP4.

Les erreurs épuisant leurs trois tentatives sont isolées dans des topics explicites :
`booking.seat-reserved.DLQ` côté Node, `payment.received.DLT` / `payment.rejected.DLT` côté
Java et les DLT de notification. Ils possèdent aussi 3 partitions afin de conserver la
partition d'origine lors de la republication.
