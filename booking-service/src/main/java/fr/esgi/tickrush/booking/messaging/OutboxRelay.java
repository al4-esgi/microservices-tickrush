package fr.esgi.tickrush.booking.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@ConditionalOnProperty(
        prefix = "tickrush.outbox",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final long sendTimeoutMs;

    public OutboxRelay(OutboxEventRepository outbox,
                       KafkaTemplate<String, String> kafka,
                       @Value("${tickrush.outbox.relay.send-timeout-ms:5000}") long sendTimeoutMs) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Transactional
    public boolean publish(long outboxId) {
        OutboxEvent event = outbox.findByIdForUpdate(outboxId).orElse(null);
        if (event == null || event.getPublishedAt() != null) {
            return false;
        }

        try {
            kafka.send(event.getTopic(), event.getEventKey(), event.getPayload())
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            event.markPublished(Instant.now());
            outbox.save(event);
            log.info("Outbox publiee: outboxId={}, eventId={}, eventType={}, reservationId={}, topic={}",
                    event.getId(), event.getEventId(), event.getEventType(),
                    event.getAggregateId(), event.getTopic());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publication Outbox interrompue: " + event.getEventId(), ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Kafka a refuse l'evenement Outbox " + event.getEventId(),
                    ex.getCause());
        } catch (TimeoutException ex) {
            throw new IllegalStateException("Timeout Kafka pour l'evenement Outbox " + event.getEventId(), ex);
        }
    }
}
