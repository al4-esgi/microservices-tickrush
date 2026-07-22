package fr.esgi.tickrush.booking;

import fr.esgi.tickrush.booking.domain.Event;
import fr.esgi.tickrush.booking.domain.InsufficientSeatsException;
import fr.esgi.tickrush.booking.domain.Reservation;
import fr.esgi.tickrush.booking.domain.ReservationService;
import fr.esgi.tickrush.booking.repository.EventRepository;
import fr.esgi.tickrush.booking.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReservationConcurrencyIntegrationTests {

    @Autowired
    private ReservationService service;

    @Autowired
    private EventRepository events;

    @Autowired
    private ReservationRepository reservations;

    @BeforeEach
    void cleanDatabase() {
        reservations.deleteAll();
        events.deleteAll();
    }

    @Test
    void concurrentRequestsNeverOversellAndUseAllAvailableSeats() throws Exception {
        UUID eventId = UUID.randomUUID();
        events.saveAndFlush(new Event(eventId, "Concert concurrent", 3, new BigDecimal("25.00")));

        int requestCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Reservation>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                int customerNumber = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return service.reserve(eventId, "client-%d@test.fr".formatted(customerNumber), 1);
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int accepted = 0;
            int refused = 0;
            for (Future<Reservation> future : futures) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                    accepted++;
                } catch (ExecutionException ex) {
                    assertThat(ex.getCause()).isInstanceOf(InsufficientSeatsException.class);
                    refused++;
                }
            }

            assertThat(accepted).isEqualTo(3);
            assertThat(refused).isEqualTo(3);
            assertThat(reservations.count()).isEqualTo(3);
            assertThat(events.findById(eventId).orElseThrow().getAvailableSeats()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }
}
