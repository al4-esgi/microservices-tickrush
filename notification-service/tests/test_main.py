from unittest.mock import patch

from fastapi.testclient import TestClient

from app.main import app

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
