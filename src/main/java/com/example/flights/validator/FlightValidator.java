package com.example.flights.validator;

import com.example.flights.model.Flight;
import com.example.flights.model.ValidationError;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Business / data validation for a SINGLE flight, in isolation.
 *
 * WHAT THIS LAYER IS RESPONSIBLE FOR:
 *   - required fields are present and not blank
 *   - delayMinutes is not negative
 *   - departureAirport != arrivalAirport
 *   - arrivalTime is after departureTime
 *   - airport codes look like IATA codes (3 uppercase letters)
 *
 * WHAT THIS LAYER DOES NOT DO:
 *   - it never looks at other records or at the database, so DUPLICATES are
 *     NOT its job. Uniqueness is a cross-record concern handled in the service
 *     layer and enforced by the primary key in the database.
 *   - it never parses strings into dates or numbers: by the time a Flight
 *     exists, every field is already the right Java type (see FlightParser).
 *
 * It collects ALL violations rather than failing on the first one, so a caller
 * can report everything wrong with a record in one pass.
 *
 * Deliberately hand-written and framework-free: it is a pure function
 * (Flight in, List<ValidationError> out) and needs no Spring context to test.
 */
@Component
public class FlightValidator {

    private static final int MAX_DELAY_MINUTES = 24 * 60;

    /** @return every rule this flight breaks; empty list means valid. */
    public List<ValidationError> validate(Flight flight) {
        List<ValidationError> errors = new ArrayList<>();

        requireText(errors, "flightId", flight.flightId());
        requireText(errors, "flightNumber", flight.flightNumber());
        requireText(errors, "airline", flight.airline());
        requireAirport(errors, "departureAirport", flight.departureAirport());
        requireAirport(errors, "arrivalAirport", flight.arrivalAirport());

        if (flight.departureTime() == null) {
            errors.add(new ValidationError("departureTime", "must not be null"));
        }
        if (flight.arrivalTime() == null) {
            errors.add(new ValidationError("arrivalTime", "must not be null"));
        }
        if (flight.status() == null) {
            errors.add(new ValidationError("status", "must not be null"));
        }

        if (flight.delayMinutes() == null) {
            errors.add(new ValidationError("delayMinutes", "must not be null"));
        } else if (flight.delayMinutes() < 0) {
            errors.add(new ValidationError("delayMinutes", "must not be negative"));
        } else if (flight.delayMinutes() > MAX_DELAY_MINUTES) {
            errors.add(new ValidationError("delayMinutes", "must not exceed " + MAX_DELAY_MINUTES));
        }

        // Cross-field rules: only run when both sides are present, so a null
        // field produces one clear error instead of two confusing ones.
        if (hasText(flight.departureAirport()) && hasText(flight.arrivalAirport())
                && flight.departureAirport().equalsIgnoreCase(flight.arrivalAirport())) {
            errors.add(new ValidationError("arrivalAirport", "must differ from departureAirport"));
        }

        if (flight.departureTime() != null && flight.arrivalTime() != null
                && !flight.arrivalTime().isAfter(flight.departureTime())) {
            errors.add(new ValidationError("arrivalTime", "must be after departureTime"));
        }

        return errors;
    }

    /** Convenience wrapper for call sites that only care yes/no. */
    public boolean isValid(Flight flight) {
        return validate(flight).isEmpty();
    }

    private void requireText(List<ValidationError> errors, String field, String value) {
        if (value == null) {
            errors.add(new ValidationError(field, "must not be null"));
        } else if (value.isBlank()) {
            errors.add(new ValidationError(field, "must not be blank"));
        }
    }

    private void requireAirport(List<ValidationError> errors, String field, String value) {
        if (value == null) {
            errors.add(new ValidationError(field, "must not be null"));
        } else if (value.isBlank()) {
            errors.add(new ValidationError(field, "must not be blank"));
        } else if (!value.matches("[A-Z]{3}")) {
            errors.add(new ValidationError(field, "must be a 3-letter uppercase IATA code"));
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
