package fr.esgi.tickrush.booking.messaging;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID aggregateId,
        T payload
) {
    public static <T> EventEnvelope<T> create(String eventType, UUID aggregateId, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, Instant.now(), aggregateId, payload);
    }
}
