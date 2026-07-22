package fr.esgi.tickrush.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Agrégat Événement — porteur du stock de places.
 * Cœur de la contrainte « ne jamais survendre » : le décrément est protégé par un
 * verrou optimiste ({@link Version}). Deux réservations concurrentes sur la dernière
 * place → l'une des deux transactions échoue (OptimisticLock) → HTTP 409.
 */
@Entity
@Table(name = "events")
public class Event {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int totalSeats;

    @Column(nullable = false)
    private int availableSeats;

    @Column(
            nullable = false,
            precision = 10,
            scale = 2,
            columnDefinition = "numeric(10,2) default 0.00"
    )
    private BigDecimal unitPrice;

    @Version
    private long version;

    protected Event() {
        // requis par JPA
    }

    public Event(UUID id, String name, int totalSeats, BigDecimal unitPrice) {
        this.id = id;
        this.name = name;
        this.totalSeats = totalSeats;
        this.availableSeats = totalSeats;
        changeUnitPrice(unitPrice);
    }

    /** Décrémente le stock de façon sûre, ou lève {@link InsufficientSeatsException}. */
    public void reserve(int quantity) {
        if (quantity > availableSeats) {
            throw new InsufficientSeatsException(id, quantity, availableSeats);
        }
        availableSeats -= quantity;
    }

    /** Remet des places en vente (expiration ou annulation), sans dépasser le total. */
    public void release(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La quantite a liberer doit etre strictement positive");
        }
        if (availableSeats + quantity > totalSeats) {
            throw new IllegalStateException("La liberation depasserait le stock total de l'evenement " + id);
        }
        availableSeats += quantity;
    }

    public void changeUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("Le prix unitaire doit etre strictement positif");
        }
        this.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public long getVersion() {
        return version;
    }
}
