package fr.esgi.tickrush.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

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

    @Version
    private long version;

    protected Event() {
        // requis par JPA
    }

    public Event(UUID id, String name, int totalSeats) {
        this.id = id;
        this.name = name;
        this.totalSeats = totalSeats;
        this.availableSeats = totalSeats;
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
        availableSeats = Math.min(totalSeats, availableSeats + quantity);
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

    public long getVersion() {
        return version;
    }
}
