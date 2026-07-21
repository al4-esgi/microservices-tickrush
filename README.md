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
| `booking-service` | Java / Spring Boot 3.5 (JDK 21) | Réservation de places, expiration (TTL), émission des billets |
| `payment-service` | Node.js / TypeScript (NestJS) | Paiement simulé (succès/échec configurable) — _séance 3_ |
| `notification-service` | _au choix_ | Confirmation par email simulé — _bonus_ |

**Flux événementiel (Kafka)** — événements clés :
`SeatReserved`, `ReservationExpired`, `PaymentReceived`, `TicketIssued`, `SeatReleased`.

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

## Lancement en développement local — booking-service

Prérequis : JDK 21+ (le projet cible Java 21 ; testé sur JDK 24).

```bash
cd booking-service
# On exclut l'auto-config JPA tant que PostgreSQL n'est pas branché (TP2)
SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration \
  ./mvnw spring-boot:run
```

Vérification :

```bash
curl http://localhost:8080/actuator/health
# attendu : {"status":"UP"}
```

## Structure du dépôt

```
microservices-tickrush/
├── README.md
├── docs/
│   └── adr/              # Architecture Decision Records (séance 7)
├── k3s/                  # manifests Kubernetes (remplace docker-compose)
├── booking-service/      # service Java — Spring Boot 3.5, JDK 21
└── payment-service/      # service Node/TS — séance 3
```
