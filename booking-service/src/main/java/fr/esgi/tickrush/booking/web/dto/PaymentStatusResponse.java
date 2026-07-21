package fr.esgi.tickrush.booking.web.dto;

import java.util.UUID;

/** Statut de paiement d'une réservation (via l'appel protégé vers payment-service). */
public record PaymentStatusResponse(UUID reservationId, String paymentStatus) {
}
