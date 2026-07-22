package fr.esgi.tickrush.booking.messaging;

import fr.esgi.tickrush.booking.domain.Event;
import fr.esgi.tickrush.booking.domain.Reservation;
import fr.esgi.tickrush.booking.domain.ReservationStatus;
import fr.esgi.tickrush.booking.repository.EventRepository;
import fr.esgi.tickrush.booking.repository.ProcessedEventRepository;
import fr.esgi.tickrush.booking.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@RecordApplicationEvents
class PaymentEventProcessorIntegrationTests {

    @Autowired
    private PaymentEventProcessor processor;

    @Autowired
    private ProcessedEventRepository processedEvents;

    @Autowired
    private ReservationRepository reservations;

    @Autowired
    private EventRepository events;

    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    void cleanDatabase() {
        processedEvents.deleteAll();
        reservations.deleteAll();
        events.deleteAll();
    }

    @Test
    void duplicatePaymentReceivedIssuesExactlyOneTicket() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        BigDecimal unitPrice = new BigDecimal("49.90");
        events.save(new Event(eventId, "Concert", 10, unitPrice));
        Reservation reservation = reservations.save(
                Reservation.open(eventId, "client@test.fr", 2, unitPrice, Duration.ofMinutes(2)));
        EventEnvelope<PaymentReceivedPayload> envelope = new EventEnvelope<>(
                messageId,
                "PaymentReceived",
                Instant.now(),
                reservation.getId(),
                new PaymentReceivedPayload(paymentId, reservation.getId(), new BigDecimal("99.80"))
        );

        assertThat(processor.processPaymentReceived(envelope)).isTrue();
        assertThat(processor.processPaymentReceived(envelope)).isFalse();

        Reservation reloaded = reservations.findById(reservation.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ReservationStatus.TICKET_ISSUED);
        assertThat(reloaded.getTicketId()).isNotNull();
        assertThat(processedEvents.count()).isEqualTo(1);
        assertThat(applicationEvents.stream(BookingApplicationEvent.class)
                .map(event -> event.envelope().eventType())
                .filter("TicketIssued"::equals))
                .hasSize(1);
    }

    @Test
    void duplicatePaymentFailureRestoresTheStockExactlyOnce() {
        UUID businessEventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        BigDecimal unitPrice = new BigDecimal("49.90");
        Event businessEvent = new Event(businessEventId, "Concert", 10, unitPrice);
        businessEvent.reserve(3);
        events.save(businessEvent);
        Reservation reservation = reservations.save(
                Reservation.open(businessEventId, "client@test.fr", 3, unitPrice, Duration.ofMinutes(2)));
        EventEnvelope<PaymentFailedPayload> envelope = new EventEnvelope<>(
                messageId,
                "PaymentFailed",
                Instant.now(),
                reservation.getId(),
                new PaymentFailedPayload(
                        paymentId,
                        reservation.getId(),
                        new BigDecimal("149.70"),
                        "AMOUNT_THRESHOLD")
        );

        assertThat(processor.processPaymentFailed(envelope)).isTrue();
        assertThat(processor.processPaymentFailed(envelope)).isFalse();

        Reservation reloaded = reservations.findById(reservation.getId()).orElseThrow();
        Event reloadedEvent = events.findById(businessEventId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reloadedEvent.getAvailableSeats()).isEqualTo(10);
        assertThat(processedEvents.count()).isEqualTo(1);
        assertThat(applicationEvents.stream(BookingApplicationEvent.class)
                .map(event -> event.envelope().eventType())
                .filter("SeatReleased"::equals))
                .hasSize(1);
    }
}
