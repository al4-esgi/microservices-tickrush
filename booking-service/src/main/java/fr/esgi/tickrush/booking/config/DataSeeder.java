package fr.esgi.tickrush.booking.config;

import fr.esgi.tickrush.booking.domain.Event;
import fr.esgi.tickrush.booking.repository.EventRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Amorce deux événements de démo au démarrage (identifiants fixes pour des curl
 * reproductibles). Le petit stock du second permet de démontrer la RéservationRefusée.
 */
@Configuration
public class DataSeeder {

    static final UUID CONCERT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID MATCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Bean
    CommandLineRunner seedEvents(EventRepository events) {
        return args -> {
            seed(events, CONCERT_ID, "Concert Metallica - AccorArena", 100, "49.90");
            seed(events, MATCH_ID, "PSG - OM (tribune limitee)", 5, "79.90");
        };
    }

    private void seed(EventRepository events, UUID id, String name, int seats, String unitPrice) {
        BigDecimal price = new BigDecimal(unitPrice);
        events.findById(id).ifPresentOrElse(existing -> {
            if (existing.getUnitPrice() == null || existing.getUnitPrice().signum() <= 0) {
                existing.changeUnitPrice(price);
                events.save(existing);
            }
        }, () -> events.save(new Event(id, name, seats, price)));
    }
}
