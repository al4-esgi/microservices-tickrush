package fr.esgi.tickrush.booking.messaging;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final String TRACE_PARENT =
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Mock
    private OutboxEventRepository outbox;

    @Mock
    private KafkaTemplate<String, String> kafka;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outbox, kafka, 100);
    }

    @Test
    void marksTheRowOnlyAfterKafkaAcknowledgesTheMessage() {
        OutboxEvent event = pendingEvent();
        when(outbox.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertThat(relay.publish(1L)).isTrue();

        assertThat(event.getPublishedAt()).isNotNull();
        verify(outbox).save(event);
        ArgumentCaptor<ProducerRecord<String, String>> record = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(record.capture());
        assertThat(record.getValue().key()).isEqualTo(event.getEventKey());
        assertThat(new String(record.getValue().headers().lastHeader("traceparent").value(),
                StandardCharsets.UTF_8))
                .isEqualTo(TRACE_PARENT);
    }

    @Test
    void keepsTheRowPendingWhenKafkaRejectsTheMessage() {
        OutboxEvent event = pendingEvent();
        when(outbox.findByIdForUpdate(2L)).thenReturn(Optional.of(event));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker indisponible")));

        assertThatThrownBy(() -> relay.publish(2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kafka a refuse");
        assertThat(event.getPublishedAt()).isNull();
        verify(outbox, never()).save(event);
    }

    @Test
    void ignoresAnAlreadyPublishedRowDuringConcurrentPolling() {
        OutboxEvent event = pendingEvent();
        event.markPublished(java.time.Instant.now());
        when(outbox.findByIdForUpdate(3L)).thenReturn(Optional.of(event));

        assertThat(relay.publish(3L)).isFalse();

        verify(kafka, never()).send(any(ProducerRecord.class));
    }

    private OutboxEvent pendingEvent() {
        UUID reservationId = UUID.randomUUID();
        EventEnvelope<Object> envelope = EventEnvelope.create(
                "SeatReserved", reservationId, new Object());
        return OutboxEvent.pending(
                envelope,
                "booking.seat-reserved",
                "{\"eventId\":\"" + envelope.eventId() + "\"}",
                new TraceContextSnapshot(TRACE_PARENT, null, null)
        );
    }
}
