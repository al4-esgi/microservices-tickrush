package fr.esgi.tickrush.booking.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void reserveDecrementsAvailableSeats() {
        Event event = new Event(EVENT_ID, "Concert", 10);

        event.reserve(3);

        assertThat(event.getAvailableSeats()).isEqualTo(7);
    }

    @Test
    void reserveRejectsAnInsufficientStockWithoutChangingIt() {
        Event event = new Event(EVENT_ID, "Concert", 2);

        assertThatThrownBy(() -> event.reserve(3))
                .isInstanceOf(InsufficientSeatsException.class)
                .hasMessageContaining("3 demandées, 2 disponibles");
        assertThat(event.getAvailableSeats()).isEqualTo(2);
    }

    @Test
    void releaseNeverExceedsTheTotalStock() {
        Event event = new Event(EVENT_ID, "Concert", 5);
        event.reserve(4);

        event.release(10);

        assertThat(event.getAvailableSeats()).isEqualTo(5);
    }
}
