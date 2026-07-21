package fr.esgi.tickrush.booking.web;

import java.time.Instant;

/** Corps d'erreur JSON uniforme. */
public record ApiError(Instant timestamp, int status, String error, String message) {
    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message);
    }
}
