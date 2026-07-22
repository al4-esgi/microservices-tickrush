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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PaymentEventProcessorIntegrationTests {

    @Autowired
    private PaymentEventProcessor processor;

    @Autowired
    private ProcessedEventRepository processedEvents;

    @Autowired
    private ReservationRepository reservations;

    @Autowired
    private EventRepository events;

    @BeforeEach
    void cleanDatabase() {
        processedEvents.deleteAll();
        reservations.deleteAll();
        events.deleteAll();
    }

    @Test
    void duplicatePaymentReceivedChangesTheStateExactlyOnce() {
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
        assertThat(reloaded.getStatus()).isEqualTo(ReservationStatus.PAID);
        assertThat(processedEvents.count()).isEqualTo(1);
    }
}
