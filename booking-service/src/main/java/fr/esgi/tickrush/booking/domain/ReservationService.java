package fr.esgi.tickrush.booking.domain;

import fr.esgi.tickrush.booking.repository.EventRepository;
import fr.esgi.tickrush.booking.repository.ReservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
public class ReservationService {

    private final EventRepository events;
    private final ReservationRepository reservations;
    private final Duration ttl;

    public ReservationService(EventRepository events,
                              ReservationRepository reservations,
                              @Value("${reservation.ttl-seconds:120}") long ttlSeconds) {
        this.events = events;
        this.reservations = reservations;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * Réserve N places : décrément sûr du stock (verrou optimiste sur l'événement) +
     * création d'une réservation PENDING avec TTL. Le tout dans une seule transaction.
     */
    @Transactional
    public Reservation reserve(UUID eventId, String customerRef, int quantity) {
        Event event = events.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Événement", eventId));
        event.reserve(quantity);              // lève InsufficientSeatsException si stock KO
        events.save(event);
        return reservations.save(Reservation.open(eventId, customerRef, quantity, ttl));
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
