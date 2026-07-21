package fr.esgi.tickrush.booking.domain;

import fr.esgi.tickrush.booking.repository.EventRepository;
import fr.esgi.tickrush.booking.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private EventRepository events;

    @Mock
    private ReservationRepository reservations;

    private ReservationService service;

    @BeforeEach
    void setUp() {
        service = new ReservationService(events, reservations, 120);
    }

    @Test
    void reserveUpdatesTheStockAndCreatesAPendingReservation() {
        UUID eventId = UUID.randomUUID();
        Event event = new Event(eventId, "Concert", 10);
        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(reservations.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Reservation result = service.reserve(eventId, "client@test.fr", 3);

        assertThat(event.getAvailableSeats()).isEqualTo(7);
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(result.getQuantity()).isEqualTo(3);
        verify(events).save(event);
        verify(reservations).save(result);
    }

    @Test
    void reserveRejectsAnUnknownEvent() {
        UUID eventId = UUID.randomUUID();
        when(events.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserve(eventId, "client@test.fr", 1))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(events, never()).save(any());
        verify(reservations, never()).save(any());
    }

    @Test
    void reserveDoesNotPersistAnythingWhenStockIsInsufficient() {
        UUID eventId = UUID.randomUUID();
        Event event = new Event(eventId, "Concert", 1);
        when(events.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.reserve(eventId, "client@test.fr", 2))
                .isInstanceOf(InsufficientSeatsException.class);
        verify(events, never()).save(any());
        verify(reservations, never()).save(any());
    }
}
