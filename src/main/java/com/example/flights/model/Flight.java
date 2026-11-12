package com.example.flights.model;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Flight domain object.
 *
 * Deliberately a plain record with NO validation annotations: this project keeps
 * a single, explicit validation path in {@link com.example.flights.validator.FlightValidator}.
 * See README for the trade-off vs. Bean Validation.
 *
 * @Builder generates Flight.builder() for readable construction; toBuilder = true
 * also generates flight.toBuilder(), which copies an existing flight so a caller
 * can override one field without re-listing the rest.
 *
 * Jackson deserializes records natively (2.12+), so no @JsonCreator is needed.
 * Fields are nullable on purpose - a missing field parses to null and is then
 * rejected by the validator, which keeps parsing and validation independent.
 */
@Builder(toBuilder = true)
public record Flight(
        String flightId,
        String flightNumber,
        String airline,
        String departureAirport,
        String arrivalAirport,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        Integer delayMinutes,
        FlightStatus status
) {
}
