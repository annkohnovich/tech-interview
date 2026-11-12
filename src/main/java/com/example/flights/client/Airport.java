package com.example.flights.client;

/**
 * flight-service's OWN view of an airport: only the fields it actually uses.
 *
 * airport-service also returns `city` and `country`; they are ignored here
 * (tolerant reader). This is exactly what the Pact contract records - the
 * provider may add fields freely, but must not remove or rename these three.
 */
public record Airport(String iata, String name, boolean active) {
}
