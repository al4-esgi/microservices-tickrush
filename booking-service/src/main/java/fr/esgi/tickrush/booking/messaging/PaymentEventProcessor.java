package fr.esgi.tickrush.booking.messaging;

import fr.esgi.tickrush.booking.domain.Event;
import fr.esgi.tickrush.booking.domain.Reservation;
import fr.esgi.tickrush.booking.domain.ResourceNotFoundException;
import fr.esgi.tickrush.booking.repository.EventRepository;
import fr.esgi.tickrush.booking.repository.ProcessedEventRepository;
import fr.esgi.tickrush.booking.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventProcessor.class);

    private final ProcessedEventRepository processedEvents;
    private final ReservationRepository reservations;
    private final EventRepository events;
    private final ApplicationEventPublisher applicationEvents;

    public PaymentEventProcessor(ProcessedEventRepository processedEvents,
                                 ReservationRepository reservations,
                                 EventRepository events,
                                 ApplicationEventPublisher applicationEvents) {
        this.processedEvents = processedEvents;
        this.reservations = reservations;
        this.events = events;
        this.applicationEvents = applicationEvents;
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

        Reservation reservation = reservations.findByIdForUpdate(event.aggregateId())
                .orElseThrow(() -> new ResourceNotFoundException("Réservation", event.aggregateId()));
        validateAmount(reservation, payload.amount());

        // Le marqueur et l'effet métier partagent exactement la même transaction SQL.
        processedEvents.saveAndFlush(
                new ProcessedEvent(event.eventId(), event.eventType(), event.aggregateId()));
        boolean ticketIssued = reservation.issueTicket();
        reservations.save(reservation);
        if (ticketIssued) {
            Event businessEvent = events.findById(reservation.getEventId())
                    .orElseThrow(() -> new ResourceNotFoundException("Événement", reservation.getEventId()));
            TicketIssuedPayload ticket = new TicketIssuedPayload(
                    reservation.getTicketId(),
                    reservation.getId(),
                    reservation.getEventId(),
                    businessEvent.getName(),
                    reservation.getCustomerRef(),
                    reservation.getQuantity(),
                    reservation.getAmount()
            );
            applicationEvents.publishEvent(new BookingApplicationEvent<>(
                    EventEnvelope.create("TicketIssued", reservation.getId(), ticket)));
        }
        log.info("PaymentReceived traite: eventId={}, reservationId={}, statut=TICKET_ISSUED, nouveauBillet={}",
                event.eventId(), event.aggregateId(), ticketIssued);
        return true;
    }

    @Transactional
    public boolean processPaymentFailed(EventEnvelope<PaymentFailedPayload> event) {
        if (processedEvents.existsById(event.eventId())) {
            log.info("Evenement deja traite: eventId={}, reservationId={}",
                    event.eventId(), event.aggregateId());
            return false;
        }

        PaymentFailedPayload payload = event.payload();
        if (!event.aggregateId().equals(payload.reservationId())) {
            throw new IllegalArgumentException("aggregateId et reservationId doivent etre identiques");
        }

        Reservation reservation = reservations.findByIdForUpdate(event.aggregateId())
                .orElseThrow(() -> new ResourceNotFoundException("Réservation", event.aggregateId()));
        validateAmount(reservation, payload.amount());

        boolean compensated = reservation.cancelAfterPaymentFailure();
        processedEvents.saveAndFlush(
                new ProcessedEvent(event.eventId(), event.eventType(), event.aggregateId()));
        if (compensated) {
            Event businessEvent = events.findByIdForUpdate(reservation.getEventId())
                    .orElseThrow(() -> new ResourceNotFoundException("Événement", reservation.getEventId()));
            businessEvent.release(reservation.getQuantity());
            reservations.save(reservation);
            events.save(businessEvent);
            SeatReleasedPayload released = new SeatReleasedPayload(
                    reservation.getId(),
                    reservation.getEventId(),
                    reservation.getQuantity(),
                    payload.reason(),
                    businessEvent.getAvailableSeats()
            );
            applicationEvents.publishEvent(new BookingApplicationEvent<>(
                    EventEnvelope.create("SeatReleased", reservation.getId(), released)));
        }
        log.info("PaymentFailed traite: eventId={}, reservationId={}, statut={}, compensation={}",
                event.eventId(), event.aggregateId(), reservation.getStatus(), compensated);
        return true;
    }

    private void validateAmount(Reservation reservation, java.math.BigDecimal amount) {
        if (reservation.getAmount().compareTo(amount) != 0) {
            throw new IllegalArgumentException("Le montant recu ne correspond pas a la reservation");
        }
    }
}
