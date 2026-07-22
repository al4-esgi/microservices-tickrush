package fr.esgi.tickrush.booking.messaging;

import java.math.BigDecimal;
import java.util.UUID;

public record SeatReservedPayload(
        UUID reservationId,
        UUID eventId,
        String customerRef,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal amount
) {
}
