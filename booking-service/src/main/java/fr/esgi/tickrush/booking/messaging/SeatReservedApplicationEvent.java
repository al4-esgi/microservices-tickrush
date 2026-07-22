package fr.esgi.tickrush.booking.messaging;

public record SeatReservedApplicationEvent(EventEnvelope<SeatReservedPayload> envelope) {
}
