package com.example.flights.parser;

/**
 * Thrown when the JSON payload cannot be turned into Flight objects at all.
 * This is a STRUCTURAL failure, not a business-rule failure.
 */
public class FlightParseException extends RuntimeException {

    public FlightParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
