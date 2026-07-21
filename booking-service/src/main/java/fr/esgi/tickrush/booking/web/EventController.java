package fr.esgi.tickrush.booking.web;

import fr.esgi.tickrush.booking.domain.ReservationService;
import fr.esgi.tickrush.booking.web.dto.EventResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/events")
public class EventController {

    private final ReservationService service;

    public EventController(ReservationService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable UUID id) {
        return EventResponse.from(service.getEvent(id));
    }
}
