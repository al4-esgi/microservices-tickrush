# Saga TickRush

## Saga chorégraphiée réalisée

La réservation est l'agrégat de corrélation. Pour tous les faits de cette saga, la clé Kafka
et `aggregateId` valent `reservationId`; l'identifiant du concert reste dans le payload.

```mermaid
sequenceDiagram
    actor Client
    participant Booking as booking-service
    participant Kafka
    participant Payment as payment-service
    participant Notification as notification-service

    Client->>Booking: POST /reservations
    Booking->>Booking: stock + réservation + Outbox (même transaction)
    Booking-->>Kafka: relayeur Outbox publie SeatReserved
    Kafka-->>Payment: SeatReserved
    alt paiement accepté
        Payment->>Payment: paiement RECEIVED + Outbox (même transaction)
        Payment-->>Kafka: relayeur Outbox publie PaymentReceived
        Kafka-->>Booking: PaymentReceived
        Booking->>Booking: billet + Outbox (même transaction)
        Booking-->>Kafka: relayeur Outbox publie TicketIssued
        Kafka-->>Notification: TicketIssued
        Notification->>Notification: email de confirmation
    else paiement refusé
        Payment->>Payment: paiement REJECTED + Outbox (même transaction)
        Payment-->>Kafka: relayeur Outbox publie PaymentFailed
        Kafka-->>Booking: PaymentFailed
        Booking->>Booking: statut CANCELLED + stock restauré + Outbox
        Booking-->>Kafka: relayeur Outbox publie SeatReleased
    else TTL atteint avant paiement
        Booking->>Booking: statut EXPIRED + stock restauré + Outbox
        Booking-->>Kafka: relayeur Outbox publie ReservationExpired
        Booking-->>Kafka: relayeur Outbox publie SeatReleased
        Kafka-->>Notification: ReservationExpired
        Notification->>Notification: email d'expiration
    end
```

Chaque mutation de `booking-service` et chaque paiement écrivent leur événement dans une
Outbox au sein de la transaction PostgreSQL locale. Les relayeurs publient ensuite avec une
sémantique at-least-once. Les résultats de paiement consommés sont dédupliqués dans
`processed_events` au sein de la même transaction que leur effet métier. Le TTL prend un
verrou pessimiste sur la réservation et la transition d'état empêche toute seconde
libération. Depuis le TP8, le contexte W3C (`traceparent`, `tracestate`, `baggage`) est
persisté avec la ligne Outbox puis restauré avant publication : la trace reste causale malgré
le polling asynchrone. La décision Outbox complète est dans
[`ADR-002`](adr/002-transactional-outbox.md).

## Machine à états d'une version orchestrée

```mermaid
stateDiagram-v2
    [*] --> EN_ATTENTE_PAIEMENT: réservation créée
    EN_ATTENTE_PAIEMENT --> EMISSION_BILLET: paiement accepté
    EMISSION_BILLET --> CONFIRMEE: billet émis
    EN_ATTENTE_PAIEMENT --> EN_COMPENSATION: paiement refusé
    EN_ATTENTE_PAIEMENT --> EN_COMPENSATION: timeout 120 s
    EN_COMPENSATION --> ANNULEE: places libérées après refus
    EN_COMPENSATION --> EXPIREE: places libérées après timeout
    CONFIRMEE --> [*]
    ANNULEE --> [*]
    EXPIREE --> [*]
```

L'orchestrateur persisterait une ligne par réservation dans une table `saga_instances` :
`reservationId`, état, étape courante, échéance, dernière commande, nombre de tentatives et
version. Il ne porterait aucune règle de prix ou de stock; il séquencerait uniquement les
participants.

Les topics de commandes seraient :

| Commande | Topic | Destinataire |
|---|---|---|
| `AuthorizePayment` | `payment.authorize.commands` | payment-service |
| `IssueTicket` | `booking.issue-ticket.commands` | booking-service |
| `ReleaseSeats` | `booking.release-seats.commands` | booking-service |
| `SendTicketNotification` | `notification.send.commands` | notification-service |

Les services répondraient par des faits (`PaymentReceived`, `PaymentFailed`, `TicketIssued`,
`SeatReleased`) corrélés par `reservationId`. Cette version gagnerait une vue unique de
l'avancement, des timeouts persistants et des relances pilotées. Elle ajouterait un composant
à développer et superviser, couplerait le coordinateur à tous les participants et risquerait
de devenir un *god service* si des règles métier y étaient déplacées.

## Démonstration k3s

```bash
task tp6:demo   # succès, compensation mesurée et rejeu idempotent
task tp6:ttl    # expiration accélérée, remise en stock, configuration restaurée
task tp6:chaos  # payment-service indisponible puis reprise automatique
task tp7:outbox # Kafka indisponible, ligne en attente puis rattrapage sans perte
task tp8:demo   # trace complète de la saga et de sa compensation dans Jaeger
```

Le scénario de compensation affiche le stock avant la réservation, après la remise en vente,
puis après le rejeu du même `PaymentFailed`. Les deux dernières valeurs doivent être égales.
