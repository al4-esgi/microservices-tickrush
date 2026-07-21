package fr.esgi.tickrush.booking.domain;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Appel synchrone sortant vers payment-service, protégé par un circuit breaker.
 * En cas de panne de la cible (timeout, service injoignable), le circuit s'ouvre et
 * le fallback renvoie "UNKNOWN" — décision métier : l'état est inconnu, pas faux.
 */
@Service
public class PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(PaymentGateway.class);

    private final RestClient paymentClient;

    public PaymentGateway(RestClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    @CircuitBreaker(name = "payment", fallbackMethod = "statusFallback")
    public String paymentStatus(UUID reservationId) {
        return paymentClient.get()
                .uri("/payments/by-reservation/{id}/status", reservationId)
                .retrieve()
                .body(String.class);
    }

    String statusFallback(UUID reservationId, Throwable t) {
        log.warn("Circuit/fallback paiement pour réservation {} : {}", reservationId, t.toString());
        return "UNKNOWN";
    }
}
