package fr.esgi.tickrush.booking.messaging;

import java.util.UUID;

public record SeatReleasedPayload(
        UUID reservationId,
        UUID eventId,
        int quantity,
        String reason,
        int availableSeats
) {
}
