# notification-service (Python / FastAPI)

3ᵉ service (bonus) de TickRush : envoie des « emails » de confirmation, capturés par
**MailDev** (faux SMTP + UI web) — aucun mail réel n'est envoyé.

## Endpoints

| Méthode | Chemin | Rôle |
|---|---|---|
| `GET` | `/health` | sonde de vivacité |
| `POST` | `/notifications/ticket-issued` | envoie l'email de confirmation de billet |

Payload :

```json
{ "to": "client@example.com", "reservationId": "<uuid>", "eventName": "Concert", "quantity": 2 }
```

## Configuration (variables d'environnement)

| Variable | Défaut | Rôle |
|---|---|---|
| `SMTP_HOST` | `localhost` | hôte SMTP (dans le cluster : `maildev`) |
| `SMTP_PORT` | `1025` | port SMTP de MailDev |
| `MAIL_FROM` | `noreply@tickrush.local` | expéditeur |

## Évolution prévue (TP6)

Le déclenchement HTTP est provisoire. Lors de la finalisation de la saga, ce service deviendra un
**consumer** des topics `booking.ticket-issued` / `booking.reservation-expired`
(`aiokafka`), réutilisant la même fonction `send_email`.
