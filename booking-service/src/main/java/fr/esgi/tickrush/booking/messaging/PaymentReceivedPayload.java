package fr.esgi.tickrush.booking.messaging;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentReceivedPayload(
        UUID paymentId,
        UUID reservationId,
        BigDecimal amount
) {
}
