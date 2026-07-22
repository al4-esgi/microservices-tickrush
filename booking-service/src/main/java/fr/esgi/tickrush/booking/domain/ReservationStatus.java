package fr.esgi.tickrush.booking.domain;

/**
 * Cycle de vie d'une réservation (découvert à l'Event Storming, cf. docs/decoupage.md).
 * PENDING       : places réservées, en attente de paiement (soumis au TTL)
 * TICKET_ISSUED : paiement reçu et billet émis
 * EXPIRED       : délai de paiement dépassé et places libérées
 * CANCELLED     : paiement refusé et places libérées
 * PAID          : ancien état TP5, conservé pour migrer les données locales existantes
 */
public enum ReservationStatus {
    PENDING,
    TICKET_ISSUED,
    PAID,
    EXPIRED,
    CANCELLED
}
