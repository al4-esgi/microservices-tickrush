package fr.esgi.tickrush.booking.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void reserveDecrementsAvailableSeats() {
        Event event = new Event(EVENT_ID, "Concert", 10, new BigDecimal("49.90"));

        event.reserve(3);

        assertThat(event.getAvailableSeats()).isEqualTo(7);
    }

    @Test
    void reserveRejectsAnInsufficientStockWithoutChangingIt() {
        Event event = new Event(EVENT_ID, "Concert", 2, new BigDecimal("49.90"));

        assertThatThrownBy(() -> event.reserve(3))
                .isInstanceOf(InsufficientSeatsException.class)
                .hasMessageContaining("3 demandées, 2 disponibles");
        assertThat(event.getAvailableSeats()).isEqualTo(2);
    }

    @Test
    void releaseRejectsAnOverflowInsteadOfHidingADuplicateCompensation() {
        Event event = new Event(EVENT_ID, "Concert", 5, new BigDecimal("49.90"));
        event.reserve(4);

        assertThatThrownBy(() -> event.release(10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("depasserait le stock total");

        assertThat(event.getAvailableSeats()).isEqualTo(1);
    }

    @Test
    void exposesTheConfiguredUnitPriceAtTwoDecimals() {
        Event event = new Event(EVENT_ID, "Concert", 5, new BigDecimal("49.9"));

        assertThat(event.getUnitPrice()).isEqualByComparingTo("49.90");
    }
}
