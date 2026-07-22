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
    void issueTicketTransitionsOnlyOnceAndKeepsTheSameTicketId() {
        Reservation reservation = Reservation.open(
                UUID.randomUUID(), "client@test.fr", 1,
                new BigDecimal("20.00"), Duration.ofMinutes(2));

        assertThat(reservation.issueTicket()).isTrue();
        UUID ticketId = reservation.getTicketId();
        assertThat(reservation.issueTicket()).isFalse();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.TICKET_ISSUED);
        assertThat(reservation.getTicketId()).isEqualTo(ticketId);
        assertThat(reservation.getTicketIssuedAt()).isNotNull();
    }

    @Test
    void expirationAndPaymentFailureAreIdempotentCompensatingTransitions() {
        Reservation expired = Reservation.open(
                UUID.randomUUID(), "client@test.fr", 1,
                new BigDecimal("20.00"), Duration.ofMinutes(2));
        Reservation cancelled = Reservation.open(
                UUID.randomUUID(), "client@test.fr", 1,
                new BigDecimal("20.00"), Duration.ofMinutes(2));

        assertThat(expired.expire(expired.getExpiresAt())).isTrue();
        assertThat(expired.expire(expired.getExpiresAt().plusSeconds(1))).isFalse();
        assertThat(expired.cancelAfterPaymentFailure()).isFalse();
        assertThat(cancelled.cancelAfterPaymentFailure()).isTrue();
        assertThat(cancelled.cancelAfterPaymentFailure()).isFalse();

        assertThat(expired.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(cancelled.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }
}
