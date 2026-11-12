package com.example.airports;

import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory airport store, seeded with every airport the flight fixtures use
 * plus one closed airport (TXL). No database: this service exists to be a
 * realistic HTTP dependency, not to be tested for persistence.
 */
@Repository
public class AirportRepository {

    private final Map<String, Airport> airports = new ConcurrentHashMap<>();

    public AirportRepository() {
        seed();
    }

    public Optional<Airport> findByIata(String iata) {
        return Optional.ofNullable(airports.get(iata));
    }

    public List<Airport> findAll() {
        return airports.values().stream()
                .sorted(Comparator.comparing(Airport::iata))
                .toList();
    }

    /** Test hook: lets contract provider states put data in place. */
    public void save(Airport airport) {
        airports.put(airport.iata(), airport);
    }

    /** Test hook: lets contract provider states remove data. */
    public void delete(String iata) {
        airports.remove(iata);
    }

    private void seed() {
        List.of(
                new Airport("AMS", "Amsterdam Schiphol", "Amsterdam", "NL", true),
                new Airport("ARN", "Stockholm Arlanda", "Stockholm", "SE", true),
                new Airport("BCN", "Barcelona El Prat", "Barcelona", "ES", true),
                new Airport("CDG", "Paris Charles de Gaulle", "Paris", "FR", true),
                new Airport("CPH", "Copenhagen Kastrup", "Copenhagen", "DK", true),
                new Airport("DUB", "Dublin", "Dublin", "IE", true),
                new Airport("FRA", "Frankfurt am Main", "Frankfurt", "DE", true),
                new Airport("IST", "Istanbul", "Istanbul", "TR", true),
                new Airport("JFK", "John F. Kennedy", "New York", "US", true),
                new Airport("KRK", "Krakow John Paul II", "Krakow", "PL", true),
                new Airport("LHR", "London Heathrow", "London", "GB", true),
                new Airport("MUC", "Munich", "Munich", "DE", true),
                new Airport("STN", "London Stansted", "London", "GB", true),
                new Airport("VIE", "Vienna", "Vienna", "AT", true),
                new Airport("WAW", "Warsaw Chopin", "Warsaw", "PL", true),
                new Airport("TXL", "Berlin Tegel", "Berlin", "DE", false)
        ).forEach(this::save);
    }
}
