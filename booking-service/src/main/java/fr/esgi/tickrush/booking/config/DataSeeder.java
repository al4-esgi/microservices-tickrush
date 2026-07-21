package fr.esgi.tickrush.booking.config;

import fr.esgi.tickrush.booking.domain.Event;
import fr.esgi.tickrush.booking.repository.EventRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
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
            if (events.count() > 0) {
                return;
            }
            events.saveAll(List.of(
                    new Event(CONCERT_ID, "Concert Metallica - AccorArena", 100),
                    new Event(MATCH_ID, "PSG - OM (tribune limitee)", 5)
            ));
        };
    }
}
