from unittest.mock import Mock

import pytest

from app.kafka_worker import KafkaNotificationWorker


def message() -> Mock:
    result = Mock()
    result.topic.return_value = "booking.ticket-issued"
    result.partition.return_value = 2
    result.offset.return_value = 42
    result.key.return_value = b"reservation-id"
    result.value.return_value = b"not-json"
    result.headers.return_value = []
    return result


def worker(handler: Mock) -> KafkaNotificationWorker:
    return KafkaNotificationWorker(
        bootstrap_servers="kafka:29092",
        group_id="notification-service",
        topics=["booking.ticket-issued"],
        handler=handler,
        max_attempts=1,
    )


def test_dlt_is_acknowledged_before_the_consumer_offset() -> None:
    handler = Mock(side_effect=ValueError("poison"))
    consumer = Mock()
    producer = Mock()
    producer.flush.return_value = 0

    def acknowledge(**kwargs) -> None:
        kwargs["on_delivery"](None, Mock())

    producer.produce.side_effect = acknowledge

    current = message()
    worker(handler)._process(consumer, producer, current)

    producer.produce.assert_called_once()
    consumer.commit.assert_called_once_with(message=current, asynchronous=False)


def test_offset_is_not_committed_when_dlt_delivery_fails() -> None:
    handler = Mock(side_effect=ValueError("poison"))
    consumer = Mock()
    producer = Mock()
    producer.flush.return_value = 0

    def reject(**kwargs) -> None:
        kwargs["on_delivery"](RuntimeError("broker indisponible"), Mock())

    producer.produce.side_effect = reject

    with pytest.raises(RuntimeError, match="Kafka a refusé"):
        worker(handler)._process(consumer, producer, message())

    consumer.commit.assert_not_called()


def test_offset_is_not_committed_when_dlt_flush_times_out() -> None:
    handler = Mock(side_effect=ValueError("poison"))
    consumer = Mock()
    producer = Mock()
    producer.flush.return_value = 1

    with pytest.raises(RuntimeError, match="Timeout"):
        worker(handler)._process(consumer, producer, message())

    consumer.commit.assert_not_called()
