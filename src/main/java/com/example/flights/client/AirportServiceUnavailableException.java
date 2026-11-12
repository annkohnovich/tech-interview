package com.example.flights.client;

/**
 * airport-service could not answer: 5xx, timeout, connection refused, or an
 * unexpected response. Maps to HTTP 503 - the flight is not invalid, we just
 * cannot check it right now.
 */
public class AirportServiceUnavailableException extends RuntimeException {

    public AirportServiceUnavailableException(String iata, Throwable cause) {
        super("Airport service unavailable while looking up " + iata, cause);
    }
}
