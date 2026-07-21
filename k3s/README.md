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

> **Une base par service** : le service Node (`payment-service`, TP3) aura SA propre base.

## Démarrer l'infra locale

```bash
# 1. Cluster local (une seule fois) — Traefik est inclus dans k3s
k3d cluster create tickrush --port "8081:80@loadbalancer" --port "8443:443@loadbalancer"

# 2. Déployer PostgreSQL
kubectl apply -f namespace.yaml
kubectl apply -f booking-db/
kubectl -n tickrush rollout status deployment/booking-db

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
