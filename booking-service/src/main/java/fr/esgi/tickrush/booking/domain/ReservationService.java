package fr.esgi.tickrush.booking.domain;

import fr.esgi.tickrush.booking.repository.EventRepository;
import fr.esgi.tickrush.booking.repository.ReservationRepository;
import fr.esgi.tickrush.booking.messaging.EventEnvelope;
import fr.esgi.tickrush.booking.messaging.BookingApplicationEvent;
import fr.esgi.tickrush.booking.messaging.SeatReservedPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
public class ReservationService {

    private final EventRepository events;
    private final ReservationRepository reservations;
    private final ApplicationEventPublisher applicationEvents;
    private final Duration ttl;

    public ReservationService(EventRepository events,
                              ReservationRepository reservations,
                              ApplicationEventPublisher applicationEvents,
                              @Value("${reservation.ttl-seconds:120}") long ttlSeconds) {
        this.events = events;
        this.reservations = reservations;
        this.applicationEvents = applicationEvents;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * Réserve N places : décrément sûr du stock (verrou optimiste sur l'événement) +
     * création d'une réservation PENDING avec TTL. Le tout dans une seule transaction.
     */
    @Transactional
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 8,
            backoff = @Backoff(delay = 10, maxDelay = 200, multiplier = 2, random = true)
    )
    public Reservation reserve(UUID eventId, String customerRef, int quantity) {
        Event event = events.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Événement", eventId));
        event.reserve(quantity);              // lève InsufficientSeatsException si stock KO
        events.save(event);
        Reservation reservation = reservations.save(
                Reservation.open(eventId, customerRef, quantity, event.getUnitPrice(), ttl));
        SeatReservedPayload payload = new SeatReservedPayload(
                reservation.getId(),
                reservation.getEventId(),
                reservation.getCustomerRef(),
                reservation.getQuantity(),
                reservation.getUnitPrice(),
                reservation.getAmount(),
                reservation.getExpiresAt()
        );
        applicationEvents.publishEvent(new BookingApplicationEvent<>(
                EventEnvelope.create("SeatReserved", reservation.getId(), payload)));
        return reservation;
    }

    @Transactional(readOnly = true)
    public Reservation getReservation(UUID id) {
        return reservations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Réservation", id));
    }

    @Transactional(readOnly = true)
    public Event getEvent(UUID id) {
        return events.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Événement", id));
    }
}
