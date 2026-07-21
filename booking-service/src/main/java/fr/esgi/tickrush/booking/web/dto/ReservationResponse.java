package fr.esgi.tickrush.booking.web.dto;

import fr.esgi.tickrush.booking.domain.Reservation;
import fr.esgi.tickrush.booking.domain.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID eventId,
        String customerRef,
        int quantity,
        ReservationStatus status,
        Instant expiresAt,
        Instant createdAt
) {
    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(), r.getEventId(), r.getCustomerRef(), r.getQuantity(),
                r.getStatus(), r.getExpiresAt(), r.getCreatedAt());
    }
}
