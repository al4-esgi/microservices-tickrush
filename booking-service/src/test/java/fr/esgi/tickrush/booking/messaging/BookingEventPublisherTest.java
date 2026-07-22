package fr.esgi.tickrush.booking.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafka;

    private ObjectMapper objectMapper;
    private BookingEventPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        publisher = new BookingEventPublisher(kafka, objectMapper, "booking.seat-reserved");
    }

    @Test
    void publishesTheStandardEnvelopeWithReservationIdAsKey() throws Exception {
        UUID reservationId = UUID.randomUUID();
        UUID businessEventId = UUID.randomUUID();
        EventEnvelope<SeatReservedPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                "SeatReserved",
                Instant.now(),
                reservationId,
                new SeatReservedPayload(
                        reservationId,
                        businessEventId,
                        "client@test.fr",
                        2,
                        new BigDecimal("49.90"),
                        new BigDecimal("99.80"))
        );
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishSeatReserved(new SeatReservedApplicationEvent(envelope));

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(
                eq("booking.seat-reserved"), eq(reservationId.toString()), value.capture());
        JsonNode json = objectMapper.readTree(value.getValue());
        assertThat(json.path("eventId").asText()).isEqualTo(envelope.eventId().toString());
        assertThat(json.path("eventType").asText()).isEqualTo("SeatReserved");
        assertThat(json.path("aggregateId").asText()).isEqualTo(reservationId.toString());
        assertThat(json.path("payload").path("eventId").asText())
                .isEqualTo(businessEventId.toString());
        assertThat(json.path("payload").path("amount").decimalValue())
                .isEqualByComparingTo("99.80");
    }
}
