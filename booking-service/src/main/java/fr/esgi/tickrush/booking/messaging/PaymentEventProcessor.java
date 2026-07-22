package fr.esgi.tickrush.booking.messaging;

import fr.esgi.tickrush.booking.domain.Reservation;
import fr.esgi.tickrush.booking.domain.ResourceNotFoundException;
import fr.esgi.tickrush.booking.repository.ProcessedEventRepository;
import fr.esgi.tickrush.booking.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventProcessor.class);

    private final ProcessedEventRepository processedEvents;
    private final ReservationRepository reservations;

    public PaymentEventProcessor(ProcessedEventRepository processedEvents,
                                 ReservationRepository reservations) {
        this.processedEvents = processedEvents;
        this.reservations = reservations;
    }

    @Transactional
    public boolean processPaymentReceived(EventEnvelope<PaymentReceivedPayload> event) {
        if (processedEvents.existsById(event.eventId())) {
            log.info("Evenement deja traite: eventId={}, reservationId={}",
                    event.eventId(), event.aggregateId());
            return false;
        }

        PaymentReceivedPayload payload = event.payload();
        if (!event.aggregateId().equals(payload.reservationId())) {
            throw new IllegalArgumentException("aggregateId et reservationId doivent etre identiques");
        }

        Reservation reservation = reservations.findById(event.aggregateId())
                .orElseThrow(() -> new ResourceNotFoundException("Réservation", event.aggregateId()));
        if (reservation.getAmount().compareTo(payload.amount()) != 0) {
            throw new IllegalArgumentException("Le montant recu ne correspond pas a la reservation");
        }

        // Le marqueur et l'effet métier partagent exactement la même transaction SQL.
        processedEvents.saveAndFlush(
                new ProcessedEvent(event.eventId(), event.eventType(), event.aggregateId()));
        reservation.markPaid();
        reservations.save(reservation);
        log.info("PaymentReceived traite: eventId={}, reservationId={}, statut=PAID",
                event.eventId(), event.aggregateId());
        return true;
    }
}
