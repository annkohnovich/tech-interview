package com.example.flights.repository;

import com.example.flights.model.Flight;
import com.example.flights.model.FlightStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for flights, using plain JdbcTemplate so every SQL statement is
 * visible (no ORM behaviour to reason about while testing).
 *
 * The FLIGHT_ID primary key is the last line of defence against duplicates:
 * even if the service check is bypassed, the database rejects the insert.
 */
@Repository
public class FlightRepository {

    private static final RowMapper<Flight> ROW_MAPPER = (ResultSet rs, int rowNum) -> new Flight(
            rs.getString("flight_id"),
            rs.getString("flight_number"),
            rs.getString("airline"),
            rs.getString("departure_airport"),
            rs.getString("arrival_airport"),
            rs.getTimestamp("departure_time").toLocalDateTime(),
            rs.getTimestamp("arrival_time").toLocalDateTime(),
            rs.getInt("delay_minutes"),
            FlightStatus.valueOf(rs.getString("status"))
    );

    private final JdbcTemplate jdbcTemplate;

    public FlightRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(Flight flight) {
        jdbcTemplate.update("""
                        INSERT INTO flights (flight_id, flight_number, airline, departure_airport,
                                             arrival_airport, departure_time, arrival_time,
                                             delay_minutes, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                flight.flightId(),
                flight.flightNumber(),
                flight.airline(),
                flight.departureAirport(),
                flight.arrivalAirport(),
                flight.departureTime(),
                flight.arrivalTime(),
                flight.delayMinutes(),
                flight.status().name());
    }

    public List<Flight> findAll() {
        return jdbcTemplate.query("SELECT * FROM flights ORDER BY departure_time", ROW_MAPPER);
    }

    public Optional<Flight> findById(String flightId) {
        return jdbcTemplate.query("SELECT * FROM flights WHERE flight_id = ?", ROW_MAPPER, flightId)
                .stream()
                .findFirst();
    }

    public boolean existsById(String flightId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flights WHERE flight_id = ?", Integer.class, flightId);
        return count != null && count > 0;
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flights", Long.class);
        return count == null ? 0L : count;
    }

    /** Test hook: gives integration tests a clean slate between cases. */
    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM flights");
    }
}
