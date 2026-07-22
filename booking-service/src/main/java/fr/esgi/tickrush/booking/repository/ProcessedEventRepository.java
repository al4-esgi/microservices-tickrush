package fr.esgi.tickrush.booking.repository;

import fr.esgi.tickrush.booking.messaging.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
