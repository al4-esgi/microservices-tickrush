# Manifests Kubernetes (k3s)

Déploiement du projet sur **k3s** (en remplacement de `docker compose`, option validée par
le formateur). Convention reprise du lab :

- un dossier par service : `deployment.yaml`, `service.yaml`, `ingress.yaml` (si exposé),
  `pvc.yaml` (si persistance) ;
- namespaces centralisés dans `namespace.yaml` ;
- Traefik en ingress controller, cert-manager pour le TLS.

À alimenter à partir de la séance 2 (Kafka, PostgreSQL) puis au fil des services.
