# TP04 - Kafka KRaft, partitions, offsets et rebalance

Le TP est adapté à l'infrastructure k3s validée par le formateur. Il conserve les objectifs
du sujet : Kafka sans ZooKeeper, Kafka UI, topics explicites, deux listeners, commandes CLI,
consumer groups, lag et rebalance.

## Infrastructure

- Broker : `apache/kafka:3.8.0`, un nœud KRaft `broker,controller`.
- UI : `provectuslabs/kafka-ui:v0.7.2`.
- Stockage : PVC `kafka-data` de 2 Gio.
- Création des topics : Job `kafka-init`, alimenté par le ConfigMap généré depuis
  `k3s/kafka/create-topics.sh`.
- Auto-création désactivée : `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`.

Le listener `INTERNAL` annonce `kafka:29092`, résolvable par les pods et Kafka UI. Le
listener `EXTERNAL` annonce `localhost:9092`; `task kafka:forward` relie ce port au poste de
développement. Une adresse annoncée doit être joignable par le client, sinon la connexion
initiale au bootstrap réussit mais les échanges suivants échouent.

## Topics et clés

| Topic | Clé | Ordre garanti pour |
|---|---|---|
| `booking.seat-reserved` | `reservationId` | le cycle de vie d'une réservation (choix final TP5) |
| `booking.reservation-expired` | `reservationId` | une réservation |
| `booking.seat-released` | `reservationId` | la compensation d'une réservation |
| `booking.ticket-issued` | `reservationId` | une réservation |
| `payment.received` | `reservationId` | un paiement |
| `payment.rejected` | `reservationId` | un paiement |

Chaque topic possède 3 partitions. Un consumer group peut donc paralléliser le traitement
sur au plus 3 consommateurs actifs; les instances supplémentaires restent sans partition.
Le facteur de réplication vaut 1 parce que le TP utilise un seul broker. Ce réglage n'est pas
acceptable en production : la perte du broker rend les données indisponibles et peut les
perdre; il faudrait plusieurs brokers avec un facteur de réplication d'au moins 3.

## Démonstration reproductible

```bash
task deploy
task kafka:topics
task kafka:produce-demo
task kafka:consume-demo
task kafka:consume:partition
task kafka:lag-demo
```

`task kafka:produce-demo` écrit 10 sondes sans effet métier avec 3 `reservationId`. Leur
`eventType=PartitionProbe` est volontairement ignoré par le consumer applicatif du TP5.
Le consumer CLI filtre ce type et affiche la dernière occurrence de chacune des 10 séquences
avec sa clé, sa partition et son offset. La démonstration reste donc lisible même après les TP
suivants sur un cluster persistant et montre toujours les trois clés choisies.
Tous les messages portant la même clé arrivent dans la même partition et leurs offsets
augmentent : l'ordre est garanti dans une partition, pas entre toutes les partitions. Le
consumer relit le journal depuis l'offset 0 avant de filtrer; consommer ne détruit pas les faits.

Pour observer un rebalance, lancer la commande suivante dans deux terminaux :

```bash
task kafka:group:consume
```

Produire ensuite de nouveaux messages avec `task kafka:produce-demo`, puis inspecter :

```bash
task kafka:group:describe
```

Les trois partitions sont réparties entre les deux membres. Après `Ctrl-C` dans un terminal,
Kafka réaffecte ses partitions au membre survivant. Un troisième consommateur lancé avec un
autre nom de groupe lit indépendamment tous les événements : dans un groupe les messages sont
répartis, entre groupes ils sont diffusés.

Observation réalisée dans le cluster local : le membre A détenait initialement les partitions
0 et 1, le membre B la partition 2. Après arrêt du membre B, le membre A détenait les trois
partitions. Le rebalance a donc bien rétabli la prise en charge complète du topic.

## Lecture des offsets

- `CURRENT-OFFSET` : prochain offset que le groupe doit lire, son marque-page.
- `LOG-END-OFFSET` : prochain offset qui sera écrit en fin de partition.
- `LAG` : `LOG-END-OFFSET - CURRENT-OFFSET`, nombre de messages restant à traiter.

`task kafka:lag-demo` recrée un groupe dédié, ne consomme que 3 messages puis affiche son lag.
La mesure observée était `CURRENT-OFFSET=3`, `LOG-END-OFFSET=7`, `LAG=4` sur la partition 0,
et `LAG=3` sur la partition 2, soit un lag total de 7. Le lag est la métrique principale pour
détecter un consommateur arrêté ou trop lent.

## Limite au terme du TP04

À la fin du TP04, les messages étaient produits et consommés uniquement avec les outils CLI.
Les producteurs applicatifs, le consumer idempotent et les dead-letter topics ont depuis été
ajoutés au TP05. L'Outbox, l'expiration automatique et l'émission du billet restent destinées
aux séances suivantes.
