package fr.esgi.tickrush.booking.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(
        prefix = "tickrush.outbox",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class BookingOutboxRecorder {

    private static final Logger log = LoggerFactory.getLogger(BookingOutboxRecorder.class);

    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;
    private final String seatReservedTopic;
    private final String ticketIssuedTopic;
    private final String reservationExpiredTopic;
    private final String seatReleasedTopic;

    public BookingOutboxRecorder(OutboxEventRepository outbox,
                                 ObjectMapper objectMapper,
                                 @Value("${tickrush.kafka.topics.seat-reserved}") String seatReservedTopic,
                                 @Value("${tickrush.kafka.topics.ticket-issued}") String ticketIssuedTopic,
                                 @Value("${tickrush.kafka.topics.reservation-expired}") String reservationExpiredTopic,
                                 @Value("${tickrush.kafka.topics.seat-released}") String seatReleasedTopic) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.seatReservedTopic = seatReservedTopic;
        this.ticketIssuedTopic = ticketIssuedTopic;
        this.reservationExpiredTopic = reservationExpiredTopic;
        this.seatReleasedTopic = seatReleasedTopic;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void record(BookingApplicationEvent<?> applicationEvent) {
        EventEnvelope<?> envelope = applicationEvent.envelope();
        String topic = topicFor(envelope.eventType());
        try {
            String value = objectMapper.writeValueAsString(envelope);
            outbox.save(OutboxEvent.pending(envelope, topic, value));
            log.debug("{} enregistre dans l'outbox: eventId={}, reservationId={}, topic={}",
                    envelope.eventType(), envelope.eventId(), envelope.aggregateId(), topic);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Impossible de serialiser " + envelope.eventType(), ex);
        }
    }

    private String topicFor(String eventType) {
        return switch (eventType) {
            case "SeatReserved" -> seatReservedTopic;
            case "TicketIssued" -> ticketIssuedTopic;
            case "ReservationExpired" -> reservationExpiredTopic;
            case "SeatReleased" -> seatReleasedTopic;
            default -> throw new IllegalArgumentException("Type d'evenement booking inconnu: " + eventType);
        };
    }
}
