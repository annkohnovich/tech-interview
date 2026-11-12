package com.example.flights.service;

/** No flight with this flightId. Maps to HTTP 404. */
public class FlightNotFoundException extends RuntimeException {

    public FlightNotFoundException(String flightId) {
        super("Flight not found: " + flightId);
    }
}
