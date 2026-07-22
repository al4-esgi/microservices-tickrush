"""Worker Kafka borné pour les notifications asynchrones."""

import logging
import threading
import time
from collections.abc import Callable

from confluent_kafka import Consumer, KafkaError, Producer, TopicPartition

logger = logging.getLogger(__name__)


class KafkaNotificationWorker:
    def __init__(
        self,
        bootstrap_servers: str,
        group_id: str,
        topics: list[str],
        handler: Callable[[bytes | None, bytes | None], None],
        max_attempts: int = 3,
    ) -> None:
        self._bootstrap_servers = bootstrap_servers
        self._group_id = group_id
        self._topics = topics
        self._handler = handler
        self._max_attempts = max_attempts
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        self._thread = threading.Thread(
            target=self._run,
            name="notification-kafka-consumer",
            daemon=True,
        )
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=10)

    def _run(self) -> None:
        consumer = Consumer(
            {
                "bootstrap.servers": self._bootstrap_servers,
                "group.id": self._group_id,
                "enable.auto.commit": False,
                "auto.offset.reset": "earliest",
                "allow.auto.create.topics": False,
            }
        )
        producer = Producer(
            {
                "bootstrap.servers": self._bootstrap_servers,
                "enable.idempotence": True,
                "acks": "all",
                "allow.auto.create.topics": False,
            }
        )
        consumer.subscribe(self._topics)
        logger.info("Consumer Kafka notification actif sur %s", ", ".join(self._topics))
        try:
            while not self._stop.is_set():
                message = consumer.poll(1.0)
                if message is None:
                    continue
                if message.error():
                    if message.error().code() != KafkaError._PARTITION_EOF:
                        logger.error("Erreur consumer Kafka: %s", message.error())
                    continue
                try:
                    self._process(consumer, producer, message)
                except Exception:  # noqa: BLE001 - ne jamais avancer après un échec DLT
                    logger.exception(
                        "Publication DLT impossible; offset non validé, nouvel essai"
                    )
                    consumer.seek(
                        TopicPartition(
                            message.topic(), message.partition(), message.offset()
                        )
                    )
                    time.sleep(1)
        finally:
            producer.flush(10)
            consumer.close()

    def _process(self, consumer: Consumer, producer: Producer, message) -> None:
        last_error = ""
        for attempt in range(1, self._max_attempts + 1):
            try:
                self._handler(message.key(), message.value())
                consumer.commit(message=message, asynchronous=False)
                return
            except Exception as exception:  # noqa: BLE001 - frontière de retry du worker
                last_error = str(exception)
                logger.warning(
                    "Échec notification Kafka %s/%s: topic=%s key=%s erreur=%s",
                    attempt,
                    self._max_attempts,
                    message.topic(),
                    message.key(),
                    last_error,
                )
                if attempt < self._max_attempts:
                    time.sleep(0.5)

        headers = list(message.headers() or [])
        headers.extend(
            [
                ("x-original-topic", message.topic().encode()),
                ("x-retry-count", str(self._max_attempts).encode()),
                ("x-error-message", last_error[:500].encode()),
            ]
        )
        delivered = False
        delivery_error: KafkaError | None = None

        def on_delivery(error: KafkaError | None, _) -> None:
            nonlocal delivered, delivery_error
            delivered = True
            delivery_error = error

        producer.produce(
            topic=f"{message.topic()}.DLT",
            key=message.key(),
            value=message.value(),
            partition=message.partition(),
            headers=headers,
            on_delivery=on_delivery,
        )
        remaining = producer.flush(10)
        if remaining != 0 or not delivered:
            raise RuntimeError("Timeout pendant la publication vers la DLT")
        if delivery_error is not None:
            raise RuntimeError(f"Kafka a refusé la publication DLT: {delivery_error}")
        consumer.commit(message=message, asynchronous=False)
        logger.error("Message déplacé vers %s.DLT", message.topic())
