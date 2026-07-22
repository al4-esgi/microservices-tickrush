package fr.esgi.tickrush.booking.messaging;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        prefix = "tickrush.kafka",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PaymentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsListener.class);

    private final ObjectMapper objectMapper;
    private final PaymentEventProcessor processor;
    private final JavaType genericEnvelopeType;

    public PaymentEventsListener(ObjectMapper objectMapper, PaymentEventProcessor processor) {
        this.objectMapper = objectMapper;
        this.processor = processor;
        this.genericEnvelopeType = objectMapper.getTypeFactory()
                .constructParametricType(EventEnvelope.class, JsonNode.class);
    }

    @KafkaListener(
            topics = {
                    "${tickrush.kafka.topics.payment-received}",
                    "${tickrush.kafka.topics.payment-failed}"
            },
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onPaymentEvent(ConsumerRecord<String, String> record) throws Exception {
        EventEnvelope<JsonNode> genericEvent = objectMapper.readValue(record.value(), genericEnvelopeType);
        validateBaseEnvelope(genericEvent);

        switch (genericEvent.eventType()) {
            case "PaymentReceived" -> processPaymentReceived(record, genericEvent);
            case "PaymentFailed", "PaymentRejected" -> processPaymentFailed(record, genericEvent);
            default -> log.debug("Type ignore sur {}: {}", record.topic(), genericEvent.eventType());
        }
    }

    private void processPaymentReceived(ConsumerRecord<String, String> record,
                                        EventEnvelope<JsonNode> genericEvent) throws Exception {
        PaymentReceivedPayload payload = objectMapper.treeToValue(
                genericEvent.payload(), PaymentReceivedPayload.class);
        EventEnvelope<PaymentReceivedPayload> event = new EventEnvelope<>(
                genericEvent.eventId(),
                genericEvent.eventType(),
                genericEvent.occurredAt(),
                genericEvent.aggregateId(),
                payload
        );
        validatePaymentPayload(event);
        validateKey(record, event.aggregateId());
        processor.processPaymentReceived(event);
    }

    private void processPaymentFailed(ConsumerRecord<String, String> record,
                                      EventEnvelope<JsonNode> genericEvent) throws Exception {
        PaymentFailedPayload payload = objectMapper.treeToValue(
                genericEvent.payload(), PaymentFailedPayload.class);
        EventEnvelope<PaymentFailedPayload> event = new EventEnvelope<>(
                genericEvent.eventId(),
                "PaymentFailed",
                genericEvent.occurredAt(),
                genericEvent.aggregateId(),
                payload
        );
        validatePaymentFailedPayload(event);
        validateKey(record, event.aggregateId());
        processor.processPaymentFailed(event);
    }

    private void validateBaseEnvelope(EventEnvelope<JsonNode> event) {
        Objects.requireNonNull(event.eventId(), "eventId obligatoire");
        Objects.requireNonNull(event.eventType(), "eventType obligatoire");
        Objects.requireNonNull(event.occurredAt(), "occurredAt obligatoire");
        Objects.requireNonNull(event.aggregateId(), "aggregateId obligatoire");
        Objects.requireNonNull(event.payload(), "payload obligatoire");
    }

    private void validatePaymentPayload(EventEnvelope<PaymentReceivedPayload> event) {
        UUID aggregateId = event.aggregateId();
        PaymentReceivedPayload payload = Objects.requireNonNull(event.payload(), "payload obligatoire");
        Objects.requireNonNull(payload.paymentId(), "paymentId obligatoire");
        Objects.requireNonNull(payload.reservationId(), "reservationId obligatoire");
        Objects.requireNonNull(payload.amount(), "amount obligatoire");
        if (!aggregateId.equals(payload.reservationId())) {
            throw new IllegalArgumentException("aggregateId et reservationId doivent etre identiques");
        }
    }

    private void validatePaymentFailedPayload(EventEnvelope<PaymentFailedPayload> event) {
        UUID aggregateId = event.aggregateId();
        PaymentFailedPayload payload = Objects.requireNonNull(event.payload(), "payload obligatoire");
        Objects.requireNonNull(payload.paymentId(), "paymentId obligatoire");
        Objects.requireNonNull(payload.reservationId(), "reservationId obligatoire");
        Objects.requireNonNull(payload.amount(), "amount obligatoire");
        if (payload.reason() == null || payload.reason().isBlank()) {
            throw new IllegalArgumentException("reason obligatoire");
        }
        if (!aggregateId.equals(payload.reservationId())) {
            throw new IllegalArgumentException("aggregateId et reservationId doivent etre identiques");
        }
    }

    private void validateKey(ConsumerRecord<String, String> record, UUID aggregateId) {
        if (!aggregateId.toString().equals(record.key())) {
            throw new IllegalArgumentException("La cle Kafka doit etre egale a aggregateId");
        }
    }
}
