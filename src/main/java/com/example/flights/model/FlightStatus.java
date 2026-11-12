package com.example.flights.model;

/**
 * Allowed flight statuses.
 *
 * NOTE: an unknown string (e.g. "FLYING") is a PARSING concern - Jackson
 * cannot map it to an enum constant, so deserialization fails before
 * business validation ever runs.
 */
public enum FlightStatus {
    SCHEDULED,
    BOARDING,
    DEPARTED,
    LANDED,
    DELAYED,
    CANCELLED
}
