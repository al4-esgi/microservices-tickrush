package fr.esgi.tickrush.booking.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentEventsListenerTest {

    @Mock
    private PaymentEventProcessor processor;

    private ObjectMapper objectMapper;
    private PaymentEventsListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        listener = new PaymentEventsListener(objectMapper, processor);
    }

    @Test
    void delegatesAValidPaymentReceivedEvent() throws Exception {
        UUID reservationId = UUID.randomUUID();
        EventEnvelope<PaymentReceivedPayload> event = new EventEnvelope<>(
                UUID.randomUUID(),
                "PaymentReceived",
                Instant.now(),
                reservationId,
                new PaymentReceivedPayload(UUID.randomUUID(), reservationId, new BigDecimal("49.90"))
        );
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "payment.received", 0, 1L, reservationId.toString(), objectMapper.writeValueAsString(event));

        listener.onPaymentEvent(record);

        ArgumentCaptor<EventEnvelope<PaymentReceivedPayload>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(processor).processPaymentReceived(captor.capture());
        EventEnvelope<PaymentReceivedPayload> received = captor.getValue();
        assertThat(received.eventId()).isEqualTo(event.eventId());
        assertThat(received.aggregateId()).isEqualTo(reservationId);
        assertThat(received.payload().amount()).isEqualByComparingTo("49.90");
    }

    @Test
    void ignoresAnUnknownEventTypeWithoutBreakingTheConsumer() throws Exception {
        UUID reservationId = UUID.randomUUID();
        EventEnvelope<String> event = new EventEnvelope<>(
                UUID.randomUUID(),
                "FuturePaymentEvent",
                Instant.now(),
                reservationId,
                "payload-d-un-futur-contrat"
        );
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "payment.received", 0, 1L, reservationId.toString(), objectMapper.writeValueAsString(event));

        listener.onPaymentEvent(record);

        verifyNoInteractions(processor);
    }

    @Test
    void rejectsMalformedJsonSoTheErrorHandlerCanRetryIt() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "payment.received", 0, 1L, UUID.randomUUID().toString(), "not-json");

        assertThatThrownBy(() -> listener.onPaymentEvent(record))
                .isInstanceOf(Exception.class);
        verifyNoInteractions(processor);
    }

    @Test
    void delegatesAValidPaymentFailedEvent() throws Exception {
        UUID reservationId = UUID.randomUUID();
        EventEnvelope<PaymentFailedPayload> event = new EventEnvelope<>(
                UUID.randomUUID(),
                "PaymentFailed",
                Instant.now(),
                reservationId,
                new PaymentFailedPayload(
                        UUID.randomUUID(), reservationId, new BigDecimal("149.70"), "AMOUNT_THRESHOLD")
        );
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "payment.rejected", 0, 2L, reservationId.toString(), objectMapper.writeValueAsString(event));

        listener.onPaymentEvent(record);

        ArgumentCaptor<EventEnvelope<PaymentFailedPayload>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(processor).processPaymentFailed(captor.capture());
        assertThat(captor.getValue().aggregateId()).isEqualTo(reservationId);
        assertThat(captor.getValue().payload().reason()).isEqualTo("AMOUNT_THRESHOLD");
    }
}
