"""notification-service — envoi d'« emails » de confirmation TickRush.

Pour l'instant déclenché en HTTP (endpoint /notifications/ticket-issued). À partir du TP6,
la même logique d'envoi sera branchée sur un consumer Kafka (topics booking.ticket-issued
et booking.reservation-expired) - voir docs/decoupage.md.
Les mails partent vers MailDev (faux SMTP + UI web) qui les capture sans rien envoyer.
"""

import os
import smtplib
from email.message import EmailMessage
from uuid import UUID

from fastapi import FastAPI, status
from pydantic import BaseModel, Field

SMTP_HOST = os.getenv("SMTP_HOST", "localhost")
SMTP_PORT = int(os.getenv("SMTP_PORT", "1025"))
MAIL_FROM = os.getenv("MAIL_FROM", "noreply@tickrush.local")

app = FastAPI(title="notification-service", version="0.1.0")


class TicketIssued(BaseModel):
    to: str = Field(min_length=3)
    reservationId: UUID
    eventName: str = Field(min_length=1)
    quantity: int = Field(default=1, ge=1, le=10)


def send_email(to: str, subject: str, body: str) -> None:
    message = EmailMessage()
    message["From"] = MAIL_FROM
    message["To"] = to
    message["Subject"] = subject
    message.set_content(body)
    with smtplib.SMTP(SMTP_HOST, SMTP_PORT) as smtp:
        smtp.send_message(message)


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/notifications/ticket-issued", status_code=status.HTTP_202_ACCEPTED)
def ticket_issued(event: TicketIssued) -> dict:
    body = (
        "Bonjour,\n\n"
        "Votre billet TickRush est confirmé !\n\n"
        f"Événement : {event.eventName}\n"
        f"Places    : {event.quantity}\n"
        f"Réservation : {event.reservationId}\n\n"
        "À bientôt,\nL'équipe TickRush"
    )
    send_email(event.to, "Votre billet TickRush", body)
    return {"status": "sent", "to": event.to, "reservationId": str(event.reservationId)}
