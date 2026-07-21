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
| `payment-service` | Node.js / TypeScript (NestJS 11) | Paiement simulé **idempotent** (succès/échec configurable) |
| `notification-service` | _au choix_ | Confirmation par email simulé — _bonus_ |

**Endpoints REST** (détails dans [docs/decoupage.md](docs/decoupage.md)) :
- booking : `POST /reservations`, `GET /reservations/{id}`, `GET /reservations/{id}/payment-status`, `GET /events/{id}`
- payment : `POST /payments`, `GET /payments/by-reservation/{id}/status`

**Communication inter-services** : `booking-service` → `payment-service` en HTTP synchrone
via le **DNS de Service Kubernetes** (`http://payment-service:3000`, aucune IP en dur),
protégé par **timeout + circuit breaker** (Resilience4j). Les faits métier passeront par
**Kafka** (séance 4-5) : `SeatReserved`, `ReservationExpired`, `PaymentReceived`,
`TicketIssued`, `SeatReleased`.

**Pattern avancé retenu** (recommandé pour le sujet, à documenter en ADR séance 7) :
réservation avec **TTL** + **Outbox** (émission fiable de `SeatReserved`) +
**idempotence** du consommateur de paiement. Le piège traité : la **concurrence sur le
stock** (verrou optimiste ou contrainte SQL) et les **doublons de messages**.

---

## Déploiement

> **Note.** L'énoncé mentionne `docker compose up`. Pour ce projet, le déploiement se fait
> sur **k3s** (Kubernetes), option **validée par le formateur** en remplacement de Docker
> Compose. Les manifests suivent la convention du lab : un dossier par service sous `k3s/`
> (`deployment` / `service` / `ingress` / `pvc`), Traefik en ingress, namespaces centralisés.

### Tout déployer dans le cluster (démo de soutenance)

```bash
# 1. Cluster local (Traefik inclus dans k3s) — une seule fois
k3d cluster create tickrush --port "8081:80@loadbalancer" --port "8443:443@loadbalancer"

# 2. Construire et importer les images (pas de registry)
docker build -t tickrush/booking-service:dev ./booking-service
docker build -t tickrush/payment-service:dev ./payment-service
k3d image import tickrush/booking-service:dev tickrush/payment-service:dev -c tickrush

# 3. Déployer : base + 2 services (chaque dossier de service inclut son ingress)
kubectl apply -f k3s/namespace.yaml
kubectl apply -f k3s/booking-db/ -f k3s/payment-service/ -f k3s/booking-service/
kubectl -n tickrush rollout status deployment/booking-service

# 4. Appeler via la façade Traefik
curl localhost:8081/events/11111111-1111-1111-1111-111111111111   # booking
curl -X POST localhost:8081/payments -H 'Content-Type: application/json' \
  -d '{"reservationId":"<uuid>","amount":42}'                     # payment
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
| Reprise, +10 s | `NONE`/`RECEIVED` | `HALF_OPEN` → `CLOSED` | normale |

Preuve dans les logs (`kubectl -n tickrush logs deployment/booking-service`) : d'abord
`ResourceAccessException` (I/O error / connect timeout = vraies tentatives), puis
`CallNotPermittedException: CircuitBreaker 'payment' is OPEN` (appels court-circuités).

## Structure du dépôt

```
microservices-tickrush/
├── README.md
├── docs/
│   ├── adr/              # Architecture Decision Records (séance 7)
│   └── decoupage.md      # Event Storming (contextes, contrats)
├── k3s/                  # manifests Kubernetes (remplace docker-compose)
│   ├── namespace.yaml
│   ├── booking-db/       # PostgreSQL du booking-service
│   ├── booking-service/  # deployment + service + ingress (image tickrush/booking-service)
│   └── payment-service/  # deployment + service + ingress (image tickrush/payment-service)
├── booking-service/      # service Java — Spring Boot 3.5, JDK 21
└── payment-service/      # service Node/TS — NestJS 11
```
