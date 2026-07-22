package fr.esgi.tickrush.booking.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.esgi.tickrush.booking.domain.Event;
import fr.esgi.tickrush.booking.domain.Reservation;
import fr.esgi.tickrush.booking.domain.ReservationService;
import fr.esgi.tickrush.booking.repository.EventRepository;
import fr.esgi.tickrush.booking.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "tickrush.kafka.enabled=false",
        "tickrush.outbox.enabled=true",
        "tickrush.outbox.relay.enabled=false",
        "tickrush.kafka.topics.seat-reserved=booking.seat-reserved",
        "tickrush.kafka.topics.ticket-issued=booking.ticket-issued",
        "tickrush.kafka.topics.reservation-expired=booking.reservation-expired",
        "tickrush.kafka.topics.seat-released=booking.seat-released",
        "reservation.expiration.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransactionalOutboxIntegrationTests {

    private static final UUID BUSINESS_EVENT_ID =
            UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Autowired
    private ReservationService reservationsService;

    @Autowired
    private ReservationRepository reservations;

    @Autowired
    private EventRepository events;

    @Autowired
    private OutboxEventRepository outbox;

    @Autowired
    private ApplicationEventPublisher applicationEvents;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        reservations.deleteAll();
        events.deleteAll();
        events.save(new Event(BUSINESS_EVENT_ID, "Concert Outbox", 10, new BigDecimal("49.90")));
    }

    @Test
    void commitsTheReservationAndItsOutboxRowTogether() throws Exception {
        Reservation reservation = reservationsService.reserve(
                BUSINESS_EVENT_ID, "outbox@esgi.fr", 2);

        assertThat(reservations.findById(reservation.getId())).isPresent();
        OutboxEvent row = outbox.findAll().getFirst();
        assertThat(row.getEventType()).isEqualTo("SeatReserved");
        assertThat(row.getAggregateId()).isEqualTo(reservation.getId());
        assertThat(row.getPublishedAt()).isNull();
        assertThat(objectMapper.readTree(row.getPayload())
                .path("payload").path("amount").decimalValue())
                .isEqualByComparingTo("99.80");
    }

    @Test
    void rollsBackBothBusinessDataAndOutboxWhenTheTransactionFails() {
        UUID rolledBackEventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            events.save(new Event(rolledBackEventId, "Evenement annule", 5, new BigDecimal("20.00")));
            applicationEvents.publishEvent(new BookingApplicationEvent<>(
                    EventEnvelope.create("SeatReserved", aggregateId, new Object())));
            throw new IllegalStateException("crash simule avant commit");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(events.findById(rolledBackEventId)).isEmpty();
        assertThat(outbox.findAll())
                .noneMatch(row -> row.getAggregateId().equals(aggregateId));
    }
}
