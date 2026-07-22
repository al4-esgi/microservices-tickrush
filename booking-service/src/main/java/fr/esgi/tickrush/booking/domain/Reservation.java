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
 * Porte le TTL ({@code expiresAt}) et les transitions de la saga de billetterie.
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

    private UUID ticketId;

    private Instant ticketIssuedAt;

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

    /** Émet un seul billet, même si plusieurs résultats de paiement sont livrés. */
    public boolean issueTicket() {
        if (status == ReservationStatus.TICKET_ISSUED) {
            return false;
        }
        if (status != ReservationStatus.PENDING && status != ReservationStatus.PAID) {
            throw new IllegalStateException("Une reservation " + status + " ne peut pas emettre de billet");
        }
        status = ReservationStatus.TICKET_ISSUED;
        ticketId = UUID.randomUUID();
        ticketIssuedAt = Instant.now();
        return true;
    }

    /** Compense un refus de paiement. EXPIRED/CANCELLED signifie que le stock est déjà restauré. */
    public boolean cancelAfterPaymentFailure() {
        if (status == ReservationStatus.CANCELLED || status == ReservationStatus.EXPIRED) {
            return false;
        }
        if (status != ReservationStatus.PENDING) {
            throw new IllegalStateException("Une reservation " + status + " ne peut pas etre annulee");
        }
        status = ReservationStatus.CANCELLED;
        return true;
    }

    /** Expire uniquement une réservation encore en attente dont l'échéance est atteinte. */
    public boolean expire(Instant now) {
        if (status != ReservationStatus.PENDING || expiresAt.isAfter(now)) {
            return false;
        }
        status = ReservationStatus.EXPIRED;
        return true;
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

    public UUID getTicketId() {
        return ticketId;
    }

    public Instant getTicketIssuedAt() {
        return ticketIssuedAt;
    }
}
