"""Notification des billets et expirations TickRush, par HTTP ou événements Kafka."""

import json
import logging
import os
import smtplib
from contextlib import asynccontextmanager
from datetime import datetime
from email.message import EmailMessage
from typing import Any
from uuid import UUID

from fastapi import FastAPI, status
from pydantic import BaseModel, Field

from app.kafka_worker import KafkaNotificationWorker

SMTP_HOST = os.getenv("SMTP_HOST", "localhost")
SMTP_PORT = int(os.getenv("SMTP_PORT", "1025"))
MAIL_FROM = os.getenv("MAIL_FROM", "noreply@tickrush.local")
KAFKA_ENABLED = os.getenv("KAFKA_ENABLED", "false").lower() == "true"
KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
KAFKA_GROUP_ID = os.getenv("KAFKA_GROUP_ID", "notification-service")

logger = logging.getLogger("uvicorn.error")
worker: KafkaNotificationWorker | None = None


@asynccontextmanager
async def lifespan(_: FastAPI):
    global worker
    if KAFKA_ENABLED:
        worker = KafkaNotificationWorker(
            bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
            group_id=KAFKA_GROUP_ID,
            topics=["booking.ticket-issued", "booking.reservation-expired"],
            handler=handle_kafka_event,
        )
        worker.start()
    yield
    if worker is not None:
        worker.stop()
        worker = None

app = FastAPI(title="notification-service", version="0.2.0", lifespan=lifespan)


class TicketIssued(BaseModel):
    to: str = Field(min_length=3)
    reservationId: UUID
    eventName: str = Field(min_length=1)
    quantity: int = Field(default=1, ge=1, le=10)


class EventEnvelope(BaseModel):
    eventId: UUID
    eventType: str = Field(min_length=1)
    occurredAt: datetime
    aggregateId: UUID
    payload: dict[str, Any]


class TicketIssuedPayload(BaseModel):
    ticketId: UUID
    reservationId: UUID
    eventId: UUID
    eventName: str = Field(min_length=1)
    customerRef: str = Field(min_length=3)
    quantity: int = Field(ge=1)
    amount: float = Field(gt=0)


class ReservationExpiredPayload(BaseModel):
    reservationId: UUID
    eventId: UUID
    eventName: str = Field(min_length=1)
    customerRef: str = Field(min_length=3)
    quantity: int = Field(ge=1)
    expiredAt: datetime


def send_email(to: str, subject: str, body: str) -> None:
    message = EmailMessage()
    message["From"] = MAIL_FROM
    message["To"] = to
    message["Subject"] = subject
    message.set_content(body)
    with smtplib.SMTP(SMTP_HOST, SMTP_PORT) as smtp:
        smtp.send_message(message)


def send_ticket_email(event: TicketIssuedPayload) -> None:
    body = (
        "Bonjour,\n\n"
        "Votre billet TickRush est confirmé !\n\n"
        f"Événement : {event.eventName}\n"
        f"Places    : {event.quantity}\n"
        f"Réservation : {event.reservationId}\n"
        f"Billet      : {event.ticketId}\n\n"
        "À bientôt,\nL'équipe TickRush"
    )
    send_email(event.customerRef, "Votre billet TickRush", body)
    logger.info(
        "Email TicketIssued envoyé: reservationId=%s ticketId=%s",
        event.reservationId,
        event.ticketId,
    )


def send_expiration_email(event: ReservationExpiredPayload) -> None:
    body = (
        "Bonjour,\n\n"
        f"Votre réservation {event.reservationId} pour {event.eventName} a expiré.\n"
        f"Les {event.quantity} place(s) ont été remises en vente.\n\n"
        "L'équipe TickRush"
    )
    send_email(event.customerRef, "Réservation TickRush expirée", body)
    logger.info("Email ReservationExpired envoyé: reservationId=%s", event.reservationId)


def handle_kafka_event(key: bytes | None, value: bytes | None) -> None:
    if key is None or value is None:
        raise ValueError("La clé et la valeur Kafka sont obligatoires")
    envelope = EventEnvelope.model_validate(json.loads(value.decode("utf-8")))
    if key.decode("utf-8") != str(envelope.aggregateId):
        raise ValueError("La clé Kafka doit être égale à aggregateId")

    if envelope.eventType == "TicketIssued":
        event = TicketIssuedPayload.model_validate(envelope.payload)
        if event.reservationId != envelope.aggregateId:
            raise ValueError("reservationId doit être égal à aggregateId")
        send_ticket_email(event)
    elif envelope.eventType == "ReservationExpired":
        event = ReservationExpiredPayload.model_validate(envelope.payload)
        if event.reservationId != envelope.aggregateId:
            raise ValueError("reservationId doit être égal à aggregateId")
        send_expiration_email(event)
    else:
        logger.info("Type d'événement ignoré: %s", envelope.eventType)


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
