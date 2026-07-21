package fr.esgi.tickrush.booking.web;

import fr.esgi.tickrush.booking.domain.PaymentGateway;
import fr.esgi.tickrush.booking.domain.Reservation;
import fr.esgi.tickrush.booking.domain.ReservationService;
import fr.esgi.tickrush.booking.web.dto.CreateReservationRequest;
import fr.esgi.tickrush.booking.web.dto.PaymentStatusResponse;
import fr.esgi.tickrush.booking.web.dto.ReservationResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/reservations")
public class ReservationController {

    private final ReservationService service;
    private final PaymentGateway paymentGateway;

    public ReservationController(ReservationService service, PaymentGateway paymentGateway) {
        this.service = service;
        this.paymentGateway = paymentGateway;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
        Reservation reservation = service.reserve(
                request.eventId(), request.customerRef(), request.quantity());
        return ResponseEntity
                .created(URI.create("/reservations/" + reservation.getId()))
                .body(ReservationResponse.from(reservation));
    }

    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable UUID id) {
        return ReservationResponse.from(service.getReservation(id));
    }

    /**
     * Statut de paiement de la réservation — interroge payment-service via un appel
     * protégé par circuit breaker (TP3). Retourne "UNKNOWN" si la cible est en panne.
     */
    @GetMapping("/{id}/payment-status")
    public PaymentStatusResponse paymentStatus(@PathVariable UUID id) {
        service.getReservation(id); // 404 si la réservation n'existe pas
        return new PaymentStatusResponse(id, paymentGateway.paymentStatus(id));
    }
}
