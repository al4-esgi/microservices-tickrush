# ADR-002 : Transactional Outbox pour les événements métier

## Statut

Accepté le 22 juillet 2026.

## Contexte

TickRush vend un stock limité. La création d'une réservation décrémente ce stock dans
PostgreSQL puis déclenche une saga Kafka. Au TP6, `booking-service` publiait après le commit
SQL : un crash ou une panne Kafka dans cette fenêtre laissait une réservation `PENDING`
sans `SeatReserved`. La donnée métier existait, mais aucune étape suivante ne pouvait la
découvrir. Inverser l'ordre aurait créé le mensonge opposé : un événement pour une
réservation finalement absente.

Le même dual-write existait dans `payment-service` entre la persistance du paiement et
`PaymentReceived` ou `PaymentFailed`. Perdre ce résultat bloquerait le billet ou la
compensation. Un `try/catch` ne rend pas deux systèmes atomiques, et une transaction Kafka
ne couvre pas PostgreSQL.

La contrainte TickRush est donc plus forte qu'un simple retry : toute mutation métier qui
produit un fait Kafka doit rendre ce fait durable dans la même transaction locale.

## Décision

Nous utilisons une **Transactional Outbox avec relayeur par polling dans chacun des deux
services producteurs** :

- `booking-service` écrit réservation, stock ou billet et une ligne `outbox` dans la même
  transaction Spring. Un `TransactionalEventListener(BEFORE_COMMIT)` sérialise l'enveloppe.
- `payment-service` écrit le paiement et son résultat dans `outbox` au sein de la même
  transaction TypeORM.
- Chaque ligne contient un identifiant séquentiel, `event_id` unique, `event_type`,
  `aggregate_id`, `topic`, `event_key`, l'enveloppe JSON, `created_at` et `published_at`.
- Un relayeur lit les lignes où `published_at IS NULL` dans l'ordre d'insertion toutes les
  500 ms, publie avec la clé `reservationId` et attend l'ACK Kafka (`acks=all`).
- Le relayeur renseigne `published_at` uniquement après cet ACK. Une erreur laisse la ligne
  en attente pour le polling suivant.
- Les publications vers les DLQ/DLT restent directes : elles ne sont couplées à aucune
  écriture métier locale.

Le crash entre l'ACK Kafka et le commit de `published_at` peut republier le même événement.
C'est intentionnel : l'Outbox garantit **at-least-once**, pas exactly-once. Les contraintes
d'unicité du paiement et la table Inbox `processed_events` de `booking-service` rendent les
consommateurs idempotents. L'ensemble Outbox + Inbox garantit donc « jamais zéro, parfois
plusieurs livraisons, un seul effet métier ».

## Options considérées

| Option | Évaluation dans TickRush | Décision |
|---|---|---|
| Conserver la publication directe du TP6 | Simple, mais une panne après le commit peut perdre `SeatReserved` ou le résultat du paiement et bloquer la saga silencieusement. | Rejetée |
| Transaction Kafka ou transaction distribuée 2PC | Une transaction Kafka ne couvre pas les bases PostgreSQL. Un 2PC entre deux bases et Kafka serait complexe, fortement couplé et non pris en charge par cette stack. | Rejetée |
| CDC avec Debezium sur le WAL PostgreSQL | Très faible latence et relayeur industriel, mais ajoute Kafka Connect/Debezium, des connecteurs et une exploitation disproportionnée pour le cluster pédagogique mono-nœud. | Alternative sérieuse reportée |
| Outbox avec polling applicatif | Une seule transaction locale, comportement observable en SQL, faible coût d'exploitation et démonstration reproductible sur k3s. | Retenue |

## Conséquences

### Positives

- Une commande reste disponible lorsque Kafka est momentanément indisponible.
- Aucune mutation métier validée ne peut exister sans événement durable correspondant.
- Le relayeur rattrape automatiquement le retard sans nouvel appel client.
- La ligne Outbox constitue une preuve exploitable pour diagnostiquer une saga bloquée.
- Le pattern réutilise l'idempotence déjà mise en place au TP5.

### Négatives acceptées

- **Latence supplémentaire** : le polling ajoute normalement jusqu'à 500 ms avant chaque
  publication, donc la cohérence reste à terme.
- **Doublons possibles** : un crash après l'ACK mais avant `published_at` republie. Tous les
  nouveaux consommateurs devront conserver une Inbox ou une autre déduplication durable.
- **Croissance des tables** : les lignes publiées sont conservées pour la preuve du TP. Une
  politique d'archivage/purge par rétention sera nécessaire en production.
- **Charge de polling** : chaque service interroge régulièrement sa base, même sans message.
  L'index `(published_at, id)` limite ce coût.
- **Composant supplémentaire** : le relayeur doit être supervisé. Un broker disponible ne
  suffit plus si le polling est arrêté ou en erreur.
- **Ordre dépendant du relayeur** : une instance relaie les lignes par identifiant croissant.
  Un passage à plusieurs instances devra préserver l'ordre par `event_key` tout en utilisant
  des verrous avec `SKIP LOCKED`.

## Validation

`task tp7:outbox` arrête le Deployment Kafka, crée une réservation avec une réponse HTTP
`201`, vérifie `published_at IS NULL`, redémarre Kafka puis contrôle automatiquement :

- `SeatReserved`, `PaymentReceived` et `TicketIssued` marqués publiés dans les deux Outbox ;
- réservation finale `TICKET_ISSUED` sans second appel client ;
- une seule ligne Inbox pour le paiement et un stock décrémenté exactement une fois.
