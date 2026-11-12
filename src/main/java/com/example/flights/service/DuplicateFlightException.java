package com.example.flights.service;

/** A flight with this flightId already exists. Maps to HTTP 409. */
public class DuplicateFlightException extends RuntimeException {

    private final String flightId;

    public DuplicateFlightException(String flightId) {
        super("Flight already exists: " + flightId);
        this.flightId = flightId;
    }

    public String getFlightId() {
        return flightId;
    }
}
