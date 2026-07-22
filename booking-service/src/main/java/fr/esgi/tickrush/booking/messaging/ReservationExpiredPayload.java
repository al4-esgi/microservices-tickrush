package fr.esgi.tickrush.booking.messaging;

import java.time.Instant;
import java.util.UUID;

public record ReservationExpiredPayload(
        UUID reservationId,
        UUID eventId,
        String eventName,
        String customerRef,
        int quantity,
        Instant expiredAt
) {
}
