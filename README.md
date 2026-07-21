# TickRush — Billetterie événementielle

Projet fil rouge — Module *Architecture Microservices*, 4ESGI-AL (2025-2026).

**Sujet 2 — TickRush** : vente de billets pour des événements à **stock limité**. La
contrainte centrale : ne **jamais** vendre plus de places qu'il n'en existe, même sous
forte charge, et ne jamais dupliquer ni perdre un paiement.

**Auteurs** : Alexandru Rusescu · Fethi Sedjai

---

## User stories minimales (démontrées en soutenance)

1. **Je réserve N places** pour un événement : le stock est décrémenté de manière **sûre**
   — deux clients ne peuvent pas obtenir la même place (concurrence maîtrisée).
2. **Si je ne paie pas dans le délai** (réservation non payée en 2 min), mes places sont
   **remises en vente** automatiquement (expiration / TTL).
3. **Après paiement, mon billet est émis** — et un même paiement rejoué n'émet jamais deux
   billets (idempotence du consommateur).

## Capacités métier (verbes métier — matière première de l'Event Storming, séance 2)

- **Consulter** les événements disponibles et le stock restant
- **Réserver** N places pour un événement
- **Payer** une réservation dans le délai imparti
- **Émettre / consulter** son billet après paiement
- **Expirer** une réservation non payée → **libérer** les places (remise en vente)
- **Annuler** une réservation
- **Notifier** la confirmation par « email » simulé _(bonus : notification-service)_

---

## Architecture cible

| Service | Langage | Rôle |
|---|---|---|
| `booking-service` | Java / Spring Boot 3.5 (JDK 21) | Réservation, stock (verrou optimiste), TTL, appel protégé vers paiement |
| `payment-service` | Node.js / TypeScript (NestJS 11) | Paiement simulé **idempotent** et **persisté** (PostgreSQL/TypeORM) |
| `notification-service` | Python / FastAPI | Confirmation par « email » (capté par **MailDev**) — _bonus, 3ᵉ langage_ |

**Endpoints REST** (détails dans [docs/decoupage.md](docs/decoupage.md)) :
- booking : `POST /reservations`, `GET /reservations/{id}`, `GET /reservations/{id}/payment-status`, `GET /events/{id}`
- payment : `POST /payments`, `GET /payments/by-reservation/{id}/status`

**Communication inter-services** : `booking-service` → `payment-service` en HTTP synchrone
via le **DNS de Service Kubernetes** (`http://payment-service:3000`, aucune IP en dur),
protégé par **timeout + circuit breaker** (Resilience4j). L'infrastructure **Kafka KRaft**,
Kafka UI et les topics explicites sont opérationnels depuis le TP4. Le branchement des
producteurs/consommateurs applicatifs arrive au TP5 : `SeatReserved`,
`ReservationExpired`, `PaymentReceived`, `TicketIssued`, `SeatReleased`.

**Pattern avancé retenu** (recommandé pour le sujet, à documenter en ADR séance 7) :
réservation avec **TTL** + **Outbox** (émission fiable de `SeatReserved`) +
**idempotence** du consommateur de paiement. Le piège traité : la **concurrence sur le
stock** (verrou optimiste ou contrainte SQL) et les **doublons de messages**.

### État fonctionnel après le TP4

- Réservation et décrément du stock atomiques, verrou optimiste avec retries bornés et test
  concurrent de non-survente.
- Paiement persistant et idempotent, y compris en cas de requêtes concurrentes.
- Appel HTTP inter-services protégé par timeout, circuit breaker et fallback métier.
- Kafka KRaft, Kafka UI, 6 topics à 3 partitions, CLI, offsets, lag et rebalance démontrables.
- Notification HTTP et MailDev disponibles comme bonus.

L'expiration automatique, l'Outbox, les producers/consumers Kafka et l'émission automatique
du billet restent volontairement au périmètre du TP5. Le scénario HTTP actuel les orchestre
manuellement et ne doit pas être présenté comme un flux événementiel déjà terminé.

---

## Déploiement

> **Note.** L'énoncé mentionne `docker compose up`. Pour ce projet, le déploiement se fait
> sur **k3s** (Kubernetes), option **validée par le formateur** en remplacement de Docker
> Compose. Les manifests suivent la convention du lab : un dossier par service sous `k3s/`
> (`deployment` / `service` / `ingress` / `pvc`), Traefik en ingress, namespaces centralisés.

### Démarrage rapide (Taskfile)

Le plus simple : un [`Taskfile.yml`](Taskfile.yml) orchestre tout ([go-task](https://taskfile.dev) — `brew install go-task`).

```bash
task up          # cluster k3d + build + import images + déploiement complet
task status      # pods / services / ingress
task test        # tests Java, Jest, FastAPI, lint et builds
task smoke       # scénario HTTP manuel TP3/TP4 via l'Ingress
task demo:reset  # remettre stocks, réservations et paiements à zéro (avec confirmation)
task front       # console de démo React (http://localhost:5173)
task kafka:topics        # lister et décrire les 6 topics
task kafka:produce-demo  # produire 10 messages avec 3 clés
task kafka:consume-demo  # afficher clé, partition et offset
task kafka:lag-demo      # créer puis observer le lag d'un groupe
task kafka:ui            # Kafka UI (http://localhost:8090)
task forward     # ouvrir tous les port-forwards (services, DBs, MailDev) en arrière-plan
task unforward   # les fermer tous
task redeploy    # rebuild + redéploiement après une modif de code
task stop        # éteindre le cluster (données conservées) — task start pour rallumer
task             # liste toutes les commandes
```

Les sections ci-dessous détaillent les étapes manuelles équivalentes.

### Kafka - TP4

Kafka utilise deux listeners : `INTERNAL` annonce `kafka:29092` aux pods du cluster;
`EXTERNAL` annonce `localhost:9092` aux clients du poste via `task kafka:forward`. Les
adresses annoncées doivent être réellement joignables par le client Kafka.

Les six topics sont créés par le Job `kafka-init` depuis le script versionné
[`k3s/kafka/create-topics.sh`](k3s/kafka/create-topics.sh), avec **3 partitions** et un
facteur de réplication **RF=1**. Trois partitions autorisent au maximum trois consommateurs
actifs dans un même groupe. RF=1 est uniquement acceptable pour ce cluster local mono-broker;
en production, la perte du broker supprimerait la disponibilité et pourrait perdre les
données, donc il faudrait plusieurs brokers et typiquement RF=3.

Une même clé Kafka est toujours dirigée vers la même partition : les offsets y augmentent et
l'ordre est conservé. `CURRENT-OFFSET` est le marque-page du groupe, `LOG-END-OFFSET` la fin
du journal et `LAG` leur différence. Lorsqu'un membre d'un groupe s'arrête, Kafka réattribue
ses partitions aux survivants lors d'un **rebalance**. Deux groupes distincts lisent chacun
la totalité des événements, indépendamment l'un de l'autre.

**Observation vérifiée dans k3s** : les 10 messages de démonstration ont été relus deux fois
depuis l'offset 0. Après une consommation limitée à 3 messages, le lag total mesuré était de
7. Avec deux membres dans `tickrush-rebalance-demo`, le premier détenait les partitions 0 et
1 et le second la partition 2; après arrêt du second, le survivant a récupéré les partitions
0, 1 et 2.

Le protocole complet de démonstration et les clés choisies par événement sont documentés dans
[`docs/tp04-kafka.md`](docs/tp04-kafka.md) et [`docs/decoupage.md`](docs/decoupage.md).

### Tout déployer dans le cluster (démo de soutenance)

```bash
# 1. Cluster local (Traefik inclus dans k3s) — une seule fois
k3d cluster create tickrush --port "8081:80@loadbalancer" --port "8443:443@loadbalancer"

# 2. Construire et importer les images (pas de registry)
docker build -t tickrush/booking-service:dev ./booking-service
docker build -t tickrush/payment-service:dev ./payment-service
docker build -t tickrush/notification-service:dev ./notification-service
k3d image import tickrush/booking-service:dev tickrush/payment-service:dev \
  tickrush/notification-service:dev -c tickrush

# 3. Déployer d'abord les dépendances
kubectl apply -f k3s/namespace.yaml
kubectl apply -f k3s/booking-db/ -f k3s/payment-db/ -f k3s/maildev/
kubectl apply -k k3s/kafka/
kubectl -n tickrush rollout status deployment/kafka
kubectl -n tickrush wait --for=condition=complete job/kafka-init --timeout=180s

# 4. Déployer les 3 services une fois leurs dépendances prêtes
kubectl apply -f k3s/payment-service/ -f k3s/booking-service/ -f k3s/notification-service/
kubectl -n tickrush rollout status deployment/booking-service

# 5. Appeler via la façade Traefik
curl localhost:8081/events/11111111-1111-1111-1111-111111111111   # booking
curl -X POST localhost:8081/payments -H 'Content-Type: application/json' \
  -d '{"reservationId":"<uuid>","amount":42}'                     # payment
curl -X POST localhost:8081/notifications/ticket-issued -H 'Content-Type: application/json' \
  -d '{"to":"a@b.c","reservationId":"<uuid>","eventName":"Concert","quantity":2}'  # email

# Consulter les mails capturés (UI web MailDev)
kubectl -n tickrush port-forward svc/maildev 1080:1080   # → http://localhost:1080
```

## Lancement en développement local — booking-service

Prérequis : JDK 21+ (le projet cible Java 21 ; testé sur JDK 24), Docker + `k3d`.

**1. Cluster k3d + PostgreSQL** (remplace `docker compose up` — voir [k3s/README](k3s/README.md)) :

```bash
k3d cluster create tickrush --port "8081:80@loadbalancer" --port "8443:443@loadbalancer"
kubectl apply -f k3s/namespace.yaml
kubectl apply -f k3s/booking-db/
kubectl -n tickrush rollout status deployment/booking-db
```

**2. Port-forward de la base** (le service tourne en local, la base dans le cluster) :

```bash
kubectl -n tickrush port-forward svc/booking-db 5432:5432   # laisser tourner
```

**3. Le service** (dans un autre terminal) :

```bash
cd booking-service && ./mvnw spring-boot:run
```

**Vérification bout-en-bout** :

```bash
curl http://localhost:8080/actuator/health          # {"status":"UP"}

# Réserver 2 places (événement seedé au démarrage)
curl -X POST localhost:8080/reservations -H "Content-Type: application/json" \
  -d '{"eventId":"11111111-1111-1111-1111-111111111111","customerRef":"alex@esgi","quantity":2}'
# → 201 + {"id":..., "status":"PENDING", "expiresAt": +2 min}

curl localhost:8080/reservations/<id>               # → 200
curl localhost:8080/events/11111111-1111-1111-1111-111111111111  # availableSeats décrémenté
```

Deux événements sont amorcés au démarrage (`DataSeeder`) : un concert (100 places) et un
match à **5 places** pour démontrer facilement le refus pour stock insuffisant (409).

## Résilience : circuit breaker (TP3)

L'appel `booking-service` → `payment-service` (`GET /reservations/{id}/payment-status`) est
protégé par **Resilience4j** : timeouts courts (connect 1 s / read 2 s) + circuit breaker
`payment` (fenêtre 10, min 5 appels, seuil d'échec 50 %, ouverture 10 s, 2 essais en
half-open). En cas de panne de la cible, le fallback renvoie **`UNKNOWN`** (« état inconnu,
pas faux »).

**Démo de la panne** (k8s : `scale --replicas=0` remplace `docker compose stop`) :

```bash
kubectl -n tickrush port-forward svc/booking-service 8080:8080 &   # accès à l'API
RID=<uuid d'une réservation>

# Cas nominal → RECEIVED, circuit CLOSED
curl localhost:8080/reservations/$RID/payment-status

kubectl -n tickrush scale deployment/payment-service --replicas=0  # 💥 panne
# rejouer ~6 fois : après 5 échecs le circuit OUVRE, les réponses deviennent instantanées
for i in $(seq 6); do curl -s localhost:8080/reservations/$RID/payment-status; echo; done
curl localhost:8080/actuator/circuitbreakers        # "state":"OPEN"

kubectl -n tickrush scale deployment/payment-service --replicas=1  # reprise
sleep 11                                             # waitDurationInOpenState
curl localhost:8080/reservations/$RID/payment-status # HALF_OPEN → CLOSED
```

**Séquence observée** `CLOSED → OPEN → HALF_OPEN → CLOSED` :

| Phase | Réponse | État circuit | Latence |
|---|---|---|---|
| Nominal | `RECEIVED` | `CLOSED` | normale |
| Panne, appels 1-4 | `UNKNOWN` (fallback) | `CLOSED` puis bascule | jusqu'au timeout |
| Panne, appels ≥ 5 | `UNKNOWN` (fallback) | `OPEN` | **instantané** (court-circuité) |
| Reprise, +10 s | `RECEIVED` | `HALF_OPEN` → `CLOSED` | normale |

Preuve dans les logs (`kubectl -n tickrush logs deployment/booking-service`) : d'abord
`ResourceAccessException` (I/O error / connect timeout = vraies tentatives), puis
`CallNotPermittedException: CircuitBreaker 'payment' is OPEN` (appels court-circuités).

## Structure du dépôt

```
microservices-tickrush/
├── README.md
├── docs/
│   ├── adr/              # Architecture Decision Records (séance 7)
│   ├── decoupage.md      # Event Storming (contextes, contrats)
│   └── tp04-kafka.md     # démonstration partitions, offsets, lag et rebalance
├── k3s/                  # manifests Kubernetes (remplace docker-compose)
│   ├── namespace.yaml
│   ├── booking-db/       # PostgreSQL du booking-service
│   ├── payment-db/       # PostgreSQL du payment-service
│   ├── maildev/          # faux SMTP + UI web (capture les emails)
│   ├── kafka/            # Kafka KRaft + UI + PVC + initialisation des topics
│   ├── booking-service/  # deployment + service + ingress (image tickrush/booking-service)
│   ├── payment-service/  # deployment + service + ingress (image tickrush/payment-service)
│   └── notification-service/  # deployment + service + ingress (image tickrush/notification-service)
├── booking-service/      # service Java — Spring Boot 3.5, JDK 21
├── payment-service/      # service Node/TS — NestJS 11
└── notification-service/ # service Python — FastAPI (envoi email via MailDev)
```
