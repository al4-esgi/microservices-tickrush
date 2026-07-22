package fr.esgi.tickrush.booking.domain;

import fr.esgi.tickrush.booking.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        prefix = "reservation.expiration",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ReservationExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationScheduler.class);

    private final ReservationRepository reservations;
    private final ReservationExpirationProcessor processor;

    public ReservationExpirationScheduler(ReservationRepository reservations,
                                          ReservationExpirationProcessor processor) {
        this.reservations = reservations;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${reservation.expiration.scan-interval-ms:1000}")
    public void expirePendingReservations() {
        Instant now = Instant.now();
        for (UUID reservationId : reservations.findExpiredIds(ReservationStatus.PENDING, now)) {
            try {
                processor.expire(reservationId, now);
            } catch (RuntimeException exception) {
                log.error("Echec d'expiration de la reservation {}", reservationId, exception);
            }
        }
    }
}
