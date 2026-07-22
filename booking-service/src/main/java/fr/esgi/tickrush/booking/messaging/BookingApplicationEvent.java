package fr.esgi.tickrush.booking.messaging;

/** Événement Spring local publié dans la transaction, puis envoyé à Kafka après commit. */
public record BookingApplicationEvent<T>(EventEnvelope<T> envelope) {
}
