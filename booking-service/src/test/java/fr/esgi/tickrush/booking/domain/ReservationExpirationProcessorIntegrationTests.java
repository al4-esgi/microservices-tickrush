package fr.esgi.tickrush.booking.domain;

import fr.esgi.tickrush.booking.messaging.BookingApplicationEvent;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@RecordApplicationEvents
class ReservationExpirationProcessorIntegrationTests {

    @Autowired
    private ReservationExpirationProcessor processor;

    @Autowired
    private ReservationRepository reservations;

    @Autowired
    private EventRepository events;

    @Autowired
    private ProcessedEventRepository processedEvents;

    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    void cleanDatabase() {
        processedEvents.deleteAll();
        reservations.deleteAll();
        events.deleteAll();
    }

    @Test
    void expirationRestoresStockAndPublishesEachFactOnlyOnce() {
        UUID eventId = UUID.randomUUID();
        BigDecimal unitPrice = new BigDecimal("49.90");
        Event event = new Event(eventId, "Concert", 10, unitPrice);
        event.reserve(2);
        events.save(event);
        Reservation reservation = reservations.save(
                Reservation.open(eventId, "client@test.fr", 2, unitPrice, Duration.ofSeconds(1)));

        assertThat(processor.expire(reservation.getId(), reservation.getExpiresAt().plusSeconds(1))).isTrue();
        assertThat(processor.expire(reservation.getId(), reservation.getExpiresAt().plusSeconds(2))).isFalse();

        assertThat(reservations.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);
        assertThat(events.findById(eventId).orElseThrow().getAvailableSeats()).isEqualTo(10);
        assertThat(applicationEvents.stream(BookingApplicationEvent.class)
                .map(applicationEvent -> applicationEvent.envelope().eventType())
                .filter(type -> type.equals("ReservationExpired") || type.equals("SeatReleased")))
                .containsExactly("ReservationExpired", "SeatReleased");
    }
}
