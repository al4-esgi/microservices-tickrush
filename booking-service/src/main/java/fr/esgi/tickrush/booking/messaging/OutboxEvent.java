package fr.esgi.tickrush.booking.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "outbox",
        indexes = @Index(name = "idx_outbox_pending", columnList = "published_at,id")
)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(nullable = false, updatable = false)
    private String topic;

    @Column(name = "event_key", nullable = false, updatable = false)
    private String eventKey;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
        // requis par JPA
    }

    private OutboxEvent(UUID eventId,
                        String eventType,
                        UUID aggregateId,
                        String topic,
                        String eventKey,
                        String payload,
                        Instant createdAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.eventKey = eventKey;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public static OutboxEvent pending(EventEnvelope<?> envelope, String topic, String payload) {
        return new OutboxEvent(
                envelope.eventId(),
                envelope.eventType(),
                envelope.aggregateId(),
                topic,
                envelope.aggregateId().toString(),
                payload,
                envelope.occurredAt()
        );
    }

    public boolean markPublished(Instant publishedAt) {
        if (this.publishedAt != null) {
            return false;
        }
        this.publishedAt = publishedAt;
        return true;
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getTopic() {
        return topic;
    }

    public String getEventKey() {
        return eventKey;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
