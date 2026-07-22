package fr.esgi.tickrush.booking.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(
        prefix = "tickrush.kafka",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingEventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final String seatReservedTopic;
    private final String ticketIssuedTopic;
    private final String reservationExpiredTopic;
    private final String seatReleasedTopic;

    public BookingEventPublisher(KafkaTemplate<String, String> kafka,
                                 ObjectMapper objectMapper,
                                 @Value("${tickrush.kafka.topics.seat-reserved}") String seatReservedTopic,
                                 @Value("${tickrush.kafka.topics.ticket-issued}") String ticketIssuedTopic,
                                 @Value("${tickrush.kafka.topics.reservation-expired}") String reservationExpiredTopic,
                                 @Value("${tickrush.kafka.topics.seat-released}") String seatReleasedTopic) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.seatReservedTopic = seatReservedTopic;
        this.ticketIssuedTopic = ticketIssuedTopic;
        this.reservationExpiredTopic = reservationExpiredTopic;
        this.seatReleasedTopic = seatReleasedTopic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(BookingApplicationEvent<?> applicationEvent) {
        EventEnvelope<?> envelope = applicationEvent.envelope();
        String topic = topicFor(envelope.eventType());
        try {
            String value = objectMapper.writeValueAsString(envelope);
            kafka.send(topic, envelope.aggregateId().toString(), value)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            // Cette fenêtre de dual-write sera supprimée par l'Outbox au TP07.
                            log.error("Echec de publication {}: eventId={}, reservationId={}",
                                    envelope.eventType(), envelope.eventId(), envelope.aggregateId(), error);
                            return;
                        }
                        log.info("{} publie: eventId={}, reservationId={}, topic={}",
                                envelope.eventType(), envelope.eventId(), envelope.aggregateId(), topic);
                    });
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
