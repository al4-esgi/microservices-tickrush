package fr.esgi.tickrush.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Agrégat Réservation — N places réservées pour un événement, en attente de paiement.
 * Porte le TTL ({@code expiresAt}) : une réservation PENDING non payée à échéance
 * sera expirée et ses places libérées (séance ultérieure : scheduler + événement Kafka).
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private String customerRef;

    @Column(nullable = false)
    private int quantity;

    @Column(
            nullable = false,
            precision = 10,
            scale = 2,
            columnDefinition = "numeric(10,2) default 0.00"
    )
    private BigDecimal unitPrice;

    @Column(
            nullable = false,
            precision = 12,
            scale = 2,
            columnDefinition = "numeric(12,2) default 0.00"
    )
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected Reservation() {
        // requis par JPA
    }

    /** Fabrique une réservation PENDING avec échéance = maintenant + TTL. */
    public static Reservation open(UUID eventId,
                                   String customerRef,
                                   int quantity,
                                   BigDecimal unitPrice,
                                   Duration ttl) {
        Reservation r = new Reservation();
        r.id = UUID.randomUUID();
        r.eventId = eventId;
        r.customerRef = customerRef;
        r.quantity = quantity;
        r.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        r.amount = r.unitPrice.multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
        r.status = ReservationStatus.PENDING;
        r.createdAt = Instant.now();
        r.expiresAt = r.createdAt.plus(ttl);
        return r;
    }

    public void markPaid() {
        if (status == ReservationStatus.PAID) {
            return;
        }
        if (status != ReservationStatus.PENDING) {
            throw new IllegalStateException("Une reservation " + status + " ne peut pas etre payee");
        }
        status = ReservationStatus.PAID;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
