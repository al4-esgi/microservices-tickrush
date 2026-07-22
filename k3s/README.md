# Infrastructure Kubernetes (k3s / k3d)

Le projet se déploie sur **Kubernetes** en remplacement de `docker compose` (option validée
par le formateur). En développement, on utilise un **cluster k3d local dédié** (`tickrush`) :
tout tourne sur la machine, la démo de soutenance est 100 % locale.

## Convention (reprise du lab)

Un dossier par composant, manifests bruts :
`deployment.yaml`, `service.yaml`, `secret.yaml` (si secrets), `pvc.yaml` (si persistance),
`ingress.yaml` (si exposé). Namespaces centralisés dans `namespace.yaml`
(namespace unique du projet : `tickrush`). Ingress via Traefik (fourni par k3s).

## Contenu actuel

| Chemin | Rôle |
|---|---|
| `namespace.yaml` | namespace `tickrush` |
| `booking-db/` | PostgreSQL du `booking-service` (secret, pvc, deployment, service) |
| `payment-db/` | PostgreSQL du `payment-service` (secret, pvc, deployment, service) |
| `maildev/` | faux SMTP (1025) + UI web (1080) qui capture les emails (image publique `maildev/maildev`) |
| `kafka/` | Kafka 3.8 KRaft, Kafka UI, PVC et Job idempotent de création des topics métier + DLQ/DLT |
| `booking-service/` | service Java (deployment + service + **ingress** `/events` `/reservations`), image `tickrush/booking-service:dev` |
| `payment-service/` | service Node (deployment + service + **ingress** `/payments`), image `tickrush/payment-service:dev` |
| `notification-service/` | service Python/FastAPI (deployment + service + **ingress** `/notifications`), image `tickrush/notification-service:dev` |

> Façade Traefik : **un `ingress.yaml` par service** (convention « un dossier par service »).
> Traefik agrège tous les Ingress → routage identique à un fichier central. En local (k3d)
> le routage est **par path sans TLS** ; pour le lab distant, passer en **host-based +
> cert-manager** (voir la note en tête de `booking-service/ingress.yaml`).

> **Une base par service** : `booking-service` → `booking-db`, `payment-service` → `payment-db`
> (persistance TypeORM). Deux bases distinctes, aucun partage de schéma.

## Images (pas de registry — import direct dans k3d)

```bash
docker build -t tickrush/booking-service:dev ../booking-service
docker build -t tickrush/payment-service:dev ../payment-service
docker build -t tickrush/notification-service:dev ../notification-service
k3d image import tickrush/booking-service:dev tickrush/payment-service:dev \
  tickrush/notification-service:dev -c tickrush
```

> Les Deployments utilisent `imagePullPolicy: IfNotPresent` : l'image importée dans k3d
> est utilisée telle quelle. Après un rebuild, refaire `k3d image import` puis
> `kubectl -n tickrush rollout restart deployment/<service>`.

## Démarrer l'infra locale

```bash
# 1. Cluster local (une seule fois) — Traefik est inclus dans k3s
k3d cluster create tickrush --port "8081:80@loadbalancer" --port "8443:443@loadbalancer"

# 2. Déployer les bases et Kafka
kubectl apply -f namespace.yaml
kubectl apply -f booking-db/ -f payment-db/ -f maildev/
kubectl apply -k kafka/
kubectl -n tickrush rollout status deployment/booking-db
kubectl -n tickrush rollout status deployment/kafka
kubectl -n tickrush wait --for=condition=complete job/kafka-init --timeout=180s

# 3. Rendre la base joignable depuis le service lancé en local (mvnw)
kubectl -n tickrush port-forward svc/booking-db 5432:5432
```

Astuces cycle de vie :

```bash
kubectl config use-context k3d-tickrush   # cibler le cluster du projet
kubectl -n tickrush get pods,svc,pvc      # état
k3d cluster stop tickrush                 # éteindre (sans supprimer les données)
k3d cluster start tickrush                # rallumer
k3d cluster delete tickrush               # tout supprimer (repart de zéro)
```

## Déploiement sur le lab distant (optionnel)

Le même jeu de manifests peut cibler le cluster du lab
(`export KUBECONFIG=~/.kube/config-lab`) où `monitoring` (Grafana) et `cert-manager`
sont déjà déployés — utile pour un déploiement « prod-like » et l'observabilité.
