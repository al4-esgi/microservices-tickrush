package fr.esgi.tickrush.booking.repository;

import fr.esgi.tickrush.booking.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
}
