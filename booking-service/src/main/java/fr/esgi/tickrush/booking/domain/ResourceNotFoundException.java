package fr.esgi.tickrush.booking.domain;

import java.util.UUID;

/** Ressource introuvable → mappé en HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, UUID id) {
        super("%s introuvable : %s".formatted(resource, id));
    }
}
