package fr.esgi.tickrush.booking.messaging;

/** Événement Spring local capturé dans l'Outbox avant le commit de la transaction métier. */
public record BookingApplicationEvent<T>(EventEnvelope<T> envelope) {
}
