package fr.esgi.tickrush.booking.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationTest {

    @Test
    void openCreatesAPendingReservationWithTheConfiguredTtl() {
        UUID eventId = UUID.randomUUID();
        Instant before = Instant.now();

        Reservation reservation = Reservation.open(
                eventId, "client@test.fr", 2, new BigDecimal("49.90"), Duration.ofMinutes(2));

        assertThat(reservation.getId()).isNotNull();
        assertThat(reservation.getEventId()).isEqualTo(eventId);
        assertThat(reservation.getCustomerRef()).isEqualTo("client@test.fr");
        assertThat(reservation.getQuantity()).isEqualTo(2);
        assertThat(reservation.getUnitPrice()).isEqualByComparingTo("49.90");
        assertThat(reservation.getAmount()).isEqualByComparingTo("99.80");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getCreatedAt()).isAfterOrEqualTo(before);
        assertThat(reservation.getExpiresAt())
                .isEqualTo(reservation.getCreatedAt().plus(Duration.ofMinutes(2)));
    }

    @Test
    void markPaidTransitionsOnlyAPendingReservation() {
        Reservation reservation = Reservation.open(
                UUID.randomUUID(), "client@test.fr", 1,
                new BigDecimal("20.00"), Duration.ofMinutes(2));

        reservation.markPaid();
        reservation.markPaid();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PAID);
    }
}
