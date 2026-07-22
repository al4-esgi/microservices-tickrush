package fr.esgi.tickrush.booking.messaging;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketIssuedPayload(
        UUID ticketId,
        UUID reservationId,
        UUID eventId,
        String eventName,
        String customerRef,
        int quantity,
        BigDecimal amount
) {
}
