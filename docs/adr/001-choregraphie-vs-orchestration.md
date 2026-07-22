# ADR-001 : coordination de la saga TickRush par chorégraphie

## Statut

Accepté le 22 juillet 2026.

## Contexte

Réserver des billets décrémente immédiatement un stock limité dans `booking-service`. Le
`payment-service` autorise ensuite le paiement. Un succès doit émettre un billet; un refus ou
l'expiration du TTL de 120 secondes doit restaurer exactement le stock réservé. Les messages
Kafka sont livrés au moins une fois et chaque service possède sa base.

Le flux principal contient deux participants métier et trois transitions utiles :
`SeatReserved`, résultat du paiement, puis `TicketIssued` ou `SeatReleased`. Le
`notification-service` écoute les faits finaux sans faire partie de la décision métier.

## Décision

Nous coordonnons la saga par **chorégraphie Kafka**. Chaque service réagit aux faits qu'il
comprend et reste propriétaire de ses invariants. `booking-service` possède le stock, le TTL
et les compensations; `payment-service` possède l'autorisation et l'unicité du paiement.
`reservationId` traverse tout le flux comme clé Kafka et `aggregateId`.

## Options considérées

### Chorégraphie

Forces dans TickRush :

- le flux métier est court, avec deux participants décisionnels et un seul échec externe;
- l'émetteur de `SeatReserved` ne connaît pas l'implémentation du paiement;
- Kafka conserve la demande pendant une indisponibilité temporaire du paiement;
- la notification peut être ajoutée comme consumer sans modifier le flux principal.

Faiblesses acceptées :

- l'état global doit être reconstitué depuis la réservation, le paiement et les événements;
- le timeout est implémenté localement par le scheduler du propriétaire de la réservation;
- ajouter remboursement, fraude et allocation nominative rendrait le flux plus difficile à lire;
- l'observabilité distribuée devient indispensable, notamment autour de `reservationId`.

### Orchestration

Forces dans TickRush :

- une machine à états persistée rendrait chaque saga et son timeout visibles en un endroit;
- les relances et les évolutions vers remboursement ou contrôle antifraude seraient explicites;
- le support pourrait répondre directement à « où en est la réservation ? ».

Coûts dans l'état actuel :

- un orchestrateur supplémentaire devrait être développé, persisté et supervisé;
- il connaîtrait `payment-service`, `booking-service` et `notification-service`;
- pour deux étapes, cette centralisation apporte plus de complexité que de valeur;
- il pourrait absorber les règles de paiement ou de stock et devenir un *god service*.

## Application de la grille du cours

| Critère | Situation TickRush | Décision induite |
|---|---|---|
| Nombre d'étapes | 2 étapes décisionnelles, puis notification | Chorégraphie |
| Chemins d'échec | refus ou TTL, même compensation simple | Chorégraphie |
| Timeouts / relances | TTL nécessaire mais possédé par booking-service | Léger signal orchestration, maîtrisé localement |
| Visibilité du dossier | le statut courant de la réservation suffit au TP | Chorégraphie |
| Ownership | chaque service possède clairement son invariant | Chorégraphie |
| Fréquence de changement | flux court et stable pour le projet | Chorégraphie |

## Conséquences

Positives : services faiblement couplés, absence de coordinateur central, reprise automatique
après indisponibilité et extension simple par de nouveaux consumers.

Négatives acceptées : état de processus dispersé, dual-write temporaire jusqu'au TP07 et
diagnostic dépendant des logs/événements. Ces risques sont réduits par l'enveloppe commune,
la clé `reservationId`, `processed_events`, les transitions idempotentes, les DLQ/DLT et les
scripts de démonstration.

La décision devra être réévaluée si le flux dépasse quatre étapes, si des compensations en
cascade apparaissent, si les délais deviennent contractuels ou si une vue centrale de chaque
saga devient une exigence opérationnelle.
