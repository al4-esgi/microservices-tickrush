from unittest.mock import patch

from fastapi.testclient import TestClient

import json

import pytest

from app.main import app, handle_kafka_event

client = TestClient(app)


def test_health() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


@patch("app.main.smtplib.SMTP")
def test_ticket_issued_sends_one_email(smtp_mock) -> None:
    response = client.post(
        "/notifications/ticket-issued",
        json={
            "to": "client@test.fr",
            "reservationId": "65bfbf4f-6795-49a5-a57b-6f4ff78f0ac1",
            "eventName": "Concert",
            "quantity": 2,
        },
    )

    assert response.status_code == 202
    assert response.json() == {
        "status": "sent",
        "to": "client@test.fr",
        "reservationId": "65bfbf4f-6795-49a5-a57b-6f4ff78f0ac1",
    }
    smtp_mock.assert_called_once()
    smtp_mock.return_value.__enter__.return_value.send_message.assert_called_once()


def test_ticket_issued_validates_uuid_and_quantity() -> None:
    response = client.post(
        "/notifications/ticket-issued",
        json={
            "to": "client@test.fr",
            "reservationId": "not-a-uuid",
            "eventName": "Concert",
            "quantity": 0,
        },
    )

    assert response.status_code == 422


@patch("app.main.send_email")
def test_kafka_ticket_issued_sends_the_confirmation(send_email_mock) -> None:
    reservation_id = "65bfbf4f-6795-49a5-a57b-6f4ff78f0ac1"
    value = json.dumps(
        {
            "eventId": "8f94db81-4ab1-4239-b69c-477f4907ffb6",
            "eventType": "TicketIssued",
            "occurredAt": "2026-07-22T10:00:00Z",
            "aggregateId": reservation_id,
            "payload": {
                "ticketId": "550e8400-e29b-41d4-a716-446655440000",
                "reservationId": reservation_id,
                "eventId": "11111111-1111-4111-8111-111111111111",
                "eventName": "Concert",
                "customerRef": "client@test.fr",
                "quantity": 2,
                "amount": 99.8,
            },
        }
    ).encode()

    handle_kafka_event(reservation_id.encode(), value)

    send_email_mock.assert_called_once()
    assert send_email_mock.call_args.args[0] == "client@test.fr"
    assert send_email_mock.call_args.args[1] == "Votre billet TickRush"


@patch("app.main.send_email")
def test_kafka_reservation_expired_sends_the_expiration(send_email_mock) -> None:
    reservation_id = "65bfbf4f-6795-49a5-a57b-6f4ff78f0ac1"
    value = json.dumps(
        {
            "eventId": "8f94db81-4ab1-4239-b69c-477f4907ffb6",
            "eventType": "ReservationExpired",
            "occurredAt": "2026-07-22T10:00:00Z",
            "aggregateId": reservation_id,
            "payload": {
                "reservationId": reservation_id,
                "eventId": "11111111-1111-4111-8111-111111111111",
                "eventName": "Concert",
                "customerRef": "client@test.fr",
                "quantity": 2,
                "expiredAt": "2026-07-22T10:00:00Z",
            },
        }
    ).encode()

    handle_kafka_event(reservation_id.encode(), value)

    send_email_mock.assert_called_once()
    assert send_email_mock.call_args.args[1] == "Réservation TickRush expirée"


def test_kafka_event_rejects_a_key_different_from_aggregate_id() -> None:
    value = json.dumps(
        {
            "eventId": "8f94db81-4ab1-4239-b69c-477f4907ffb6",
            "eventType": "FutureEvent",
            "occurredAt": "2026-07-22T10:00:00Z",
            "aggregateId": "65bfbf4f-6795-49a5-a57b-6f4ff78f0ac1",
            "payload": {},
        }
    ).encode()

    with pytest.raises(ValueError, match="clé Kafka"):
        handle_kafka_event(b"11111111-1111-4111-8111-111111111111", value)
