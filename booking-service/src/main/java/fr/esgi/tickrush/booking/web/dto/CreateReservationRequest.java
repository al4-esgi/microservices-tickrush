package fr.esgi.tickrush.booking.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Payload de création d'une réservation (Bean Validation). */
public record CreateReservationRequest(

        @NotNull(message = "eventId est obligatoire")
        UUID eventId,

        @NotBlank(message = "customerRef est obligatoire")
        String customerRef,

        @Positive(message = "quantity doit être > 0")
        @Max(value = 10, message = "quantity ne peut pas dépasser 10")
        int quantity
) {
}
