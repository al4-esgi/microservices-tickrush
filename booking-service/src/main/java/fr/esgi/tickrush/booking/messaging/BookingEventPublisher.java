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

    public BookingEventPublisher(KafkaTemplate<String, String> kafka,
                                 ObjectMapper objectMapper,
                                 @Value("${tickrush.kafka.topics.seat-reserved}") String seatReservedTopic) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.seatReservedTopic = seatReservedTopic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishSeatReserved(SeatReservedApplicationEvent applicationEvent) {
        EventEnvelope<SeatReservedPayload> envelope = applicationEvent.envelope();
        try {
            String value = objectMapper.writeValueAsString(envelope);
            kafka.send(seatReservedTopic, envelope.aggregateId().toString(), value)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            // Cette fenêtre de dual-write sera supprimée par l'Outbox au TP07.
                            log.error("Echec de publication SeatReserved: eventId={}, reservationId={}",
                                    envelope.eventId(), envelope.aggregateId(), error);
                            return;
                        }
                        log.info("SeatReserved publie: eventId={}, reservationId={}, amount={}",
                                envelope.eventId(), envelope.aggregateId(), envelope.payload().amount());
                    });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Impossible de serialiser SeatReserved", ex);
        }
    }
}
