package fr.esgi.tickrush.booking.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnBean(OutboxRelay.class)
@ConditionalOnProperty(
        prefix = "tickrush.outbox.relay",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxEventRepository outbox;
    private final OutboxRelay relay;
    private final int batchSize;

    public OutboxRelayScheduler(OutboxEventRepository outbox,
                                OutboxRelay relay,
                                @Value("${tickrush.outbox.relay.batch-size:50}") int batchSize) {
        this.outbox = outbox;
        this.relay = relay;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${tickrush.outbox.relay.fixed-delay-ms:500}",
            initialDelayString = "${tickrush.outbox.relay.initial-delay-ms:500}"
    )
    public void relayPending() {
        List<Long> pendingIds = outbox.findPendingIds(PageRequest.of(0, batchSize));
        for (Long pendingId : pendingIds) {
            try {
                relay.publish(pendingId);
            } catch (RuntimeException ex) {
                log.warn("Outbox en attente, nouvel essai au prochain polling: outboxId={}, erreur={}",
                        pendingId, ex.getMessage());
                break;
            }
        }
    }
}
