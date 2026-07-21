package fr.esgi.tickrush.booking.web.dto;

import fr.esgi.tickrush.booking.domain.Event;

import java.util.UUID;

public record EventResponse(
        UUID id,
        String name,
        int totalSeats,
        int availableSeats
) {
    public static EventResponse from(Event e) {
        return new EventResponse(e.getId(), e.getName(), e.getTotalSeats(), e.getAvailableSeats());
    }
}
