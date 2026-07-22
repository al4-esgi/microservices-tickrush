package fr.esgi.tickrush.booking.domain;

import fr.esgi.tickrush.booking.messaging.BookingApplicationEvent;
import fr.esgi.tickrush.booking.messaging.EventEnvelope;
import fr.esgi.tickrush.booking.messaging.ReservationExpiredPayload;
import fr.esgi.tickrush.booking.messaging.SeatReleasedPayload;
import fr.esgi.tickrush.booking.repository.EventRepository;
import fr.esgi.tickrush.booking.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ReservationExpirationProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationProcessor.class);

    private final ReservationRepository reservations;
    private final EventRepository events;
    private final ApplicationEventPublisher applicationEvents;

    public ReservationExpirationProcessor(ReservationRepository reservations,
                                          EventRepository events,
                                          ApplicationEventPublisher applicationEvents) {
        this.reservations = reservations;
        this.events = events;
        this.applicationEvents = applicationEvents;
    }

    @Transactional
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 20, maxDelay = 200, multiplier = 2)
    )
    public boolean expire(UUID reservationId, Instant now) {
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Réservation", reservationId));
        if (!reservation.expire(now)) {
            return false;
        }

        Event event = events.findByIdForUpdate(reservation.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Événement", reservation.getEventId()));
        event.release(reservation.getQuantity());
        reservations.save(reservation);
        events.save(event);

        ReservationExpiredPayload expired = new ReservationExpiredPayload(
                reservation.getId(),
                reservation.getEventId(),
                event.getName(),
                reservation.getCustomerRef(),
                reservation.getQuantity(),
                now
        );
        SeatReleasedPayload released = new SeatReleasedPayload(
                reservation.getId(),
                reservation.getEventId(),
                reservation.getQuantity(),
                "TTL_EXPIRED",
                event.getAvailableSeats()
        );
        applicationEvents.publishEvent(new BookingApplicationEvent<>(
                EventEnvelope.create("ReservationExpired", reservation.getId(), expired)));
        applicationEvents.publishEvent(new BookingApplicationEvent<>(
                EventEnvelope.create("SeatReleased", reservation.getId(), released)));

        log.info("ReservationExpired traitee: reservationId={}, quantite={}, stock={}",
                reservation.getId(), reservation.getQuantity(), event.getAvailableSeats());
        return true;
    }
}
