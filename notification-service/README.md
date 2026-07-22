# notification-service (Python / FastAPI)

3ᵉ service (bonus) de TickRush : envoie des « emails » de confirmation, capturés par
**MailDev** (faux SMTP + UI web) — aucun mail réel n'est envoyé.

Depuis le TP06, il consomme `booking.ticket-issued` et `booking.reservation-expired` avec le
groupe `notification-service`. La clé doit être égale à l'`aggregateId`; l'offset n'est validé
qu'après l'envoi SMTP. Après trois erreurs, le message rejoint la DLT du topic d'origine.

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
| `KAFKA_ENABLED` | `false` | active le worker Kafka |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | brokers Kafka |
| `KAFKA_GROUP_ID` | `notification-service` | groupe du consumer |
