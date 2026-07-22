package fr.esgi.tickrush.booking.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingOutboxRecorderTest {

    @Mock
    private OutboxEventRepository outbox;

    private ObjectMapper objectMapper;
    private BookingOutboxRecorder recorder;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        recorder = new BookingOutboxRecorder(
                outbox,
                objectMapper,
                "booking.seat-reserved",
                "booking.ticket-issued",
                "booking.reservation-expired",
                "booking.seat-released");
    }

    @Test
    void recordsTheStandardEnvelopeWithReservationIdAsKey() throws Exception {
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
                        new BigDecimal("99.80"),
                        Instant.now().plusSeconds(120))
        );

        recorder.record(new BookingApplicationEvent<>(envelope));

        ArgumentCaptor<OutboxEvent> captured = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(captured.capture());
        OutboxEvent row = captured.getValue();
        assertThat(row.getEventId()).isEqualTo(envelope.eventId());
        assertThat(row.getAggregateId()).isEqualTo(reservationId);
        assertThat(row.getEventKey()).isEqualTo(reservationId.toString());
        assertThat(row.getTopic()).isEqualTo("booking.seat-reserved");
        assertThat(row.getPublishedAt()).isNull();

        JsonNode json = objectMapper.readTree(row.getPayload());
        assertThat(json.path("eventType").asText()).isEqualTo("SeatReserved");
        assertThat(json.path("aggregateId").asText()).isEqualTo(reservationId.toString());
        assertThat(json.path("payload").path("eventId").asText())
                .isEqualTo(businessEventId.toString());
        assertThat(json.path("payload").path("amount").decimalValue())
                .isEqualByComparingTo("99.80");
    }

    @Test
    void routesACompensationToSeatReleased() {
        UUID reservationId = UUID.randomUUID();
        EventEnvelope<SeatReleasedPayload> envelope = EventEnvelope.create(
                "SeatReleased",
                reservationId,
                new SeatReleasedPayload(reservationId, UUID.randomUUID(), 3, "AMOUNT_THRESHOLD", 100));

        recorder.record(new BookingApplicationEvent<>(envelope));

        ArgumentCaptor<OutboxEvent> captured = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(captured.capture());
        assertThat(captured.getValue().getTopic()).isEqualTo("booking.seat-released");
    }

    @ParameterizedTest
    @CsvSource({
            "TicketIssued, booking.ticket-issued",
            "ReservationExpired, booking.reservation-expired"
    })
    void routesEveryTerminalFact(String eventType, String expectedTopic) {
        EventEnvelope<Object> envelope = EventEnvelope.create(
                eventType, UUID.randomUUID(), java.util.Map.of("fixture", eventType));

        recorder.record(new BookingApplicationEvent<>(envelope));

        ArgumentCaptor<OutboxEvent> captured = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(captured.capture());
        assertThat(captured.getValue().getTopic()).isEqualTo(expectedTopic);
    }

    @Test
    void rejectsAnUnknownEventTypeBeforeTheTransactionCanCommit() {
        EventEnvelope<Object> envelope = EventEnvelope.create(
                "UnknownEvent", UUID.randomUUID(), new Object());

        assertThatThrownBy(() -> recorder.record(new BookingApplicationEvent<>(envelope)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UnknownEvent");
    }
}
