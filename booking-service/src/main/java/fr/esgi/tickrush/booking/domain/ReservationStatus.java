package fr.esgi.tickrush.booking.domain;

/**
 * Cycle de vie d'une réservation (découvert à l'Event Storming, cf. docs/decoupage.md).
 * PENDING  : places réservées, en attente de paiement (soumis au TTL)
 * PAID     : paiement reçu → billet émis
 * EXPIRED  : délai de paiement dépassé → places libérées
 * CANCELLED: annulée par le client → places libérées
 */
public enum ReservationStatus {
    PENDING,
    PAID,
    EXPIRED,
    CANCELLED
}
