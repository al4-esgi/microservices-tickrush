package fr.esgi.tickrush.booking.repository;

import fr.esgi.tickrush.booking.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
}
