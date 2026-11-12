package com.example.airports;

/**
 * Airport reference data.
 *
 * `active = false` means the airport exists but is closed (e.g. TXL, Berlin
 * Tegel). flight-service treats that differently from "does not exist".
 */
public record Airport(
        String iata,
        String name,
        String city,
        String country,
        boolean active
) {
}
