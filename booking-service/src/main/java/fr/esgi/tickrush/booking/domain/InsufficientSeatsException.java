package fr.esgi.tickrush.booking.domain;

import java.util.UUID;

/** Stock insuffisant pour honorer la réservation → événement métier RéservationRefusée. */
public class InsufficientSeatsException extends RuntimeException {
    public InsufficientSeatsException(UUID eventId, int requested, int available) {
        super("Stock insuffisant pour l'événement %s : %d demandées, %d disponibles"
                .formatted(eventId, requested, available));
    }
}
