package com.example.flights.service;

import com.example.flights.client.Airport;
import com.example.flights.client.AirportClient;
import com.example.flights.model.Flight;
import com.example.flights.model.IngestionReport;
import com.example.flights.model.ValidationError;
import com.example.flights.parser.FlightParser;
import com.example.flights.repository.FlightRepository;
import com.example.flights.validator.FlightValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Orchestrates the ingestion flow: read -> parse -> validate -> persist -> report.
 *
 * ==================== INVALID-RECORD POLICY ====================
 *
 * 1. PARSE FAILURE (structural) is FAIL-FAST for the whole file.
 *    A FlightParseException propagates, NOTHING is persisted, and the caller
 *    gets HTTP 400. Rationale: a structurally broken file means a broken
 *    producer; importing half of it would hide the real problem.
 *
 * 2. VALIDATION FAILURE (business) is PER RECORD and non-fatal.
 *    Valid records are persisted, invalid ones are skipped and returned in the
 *    IngestionReport with their reasons. The import as a whole still returns
 *    HTTP 200 - the report tells the caller exactly what was dropped.
 *    Rationale: one bad row should not block a night's worth of good data.
 *
 * 3. DUPLICATES ARE REJECTED (never updated, never overwritten).
 *    A flightId is unique and immutable. Two cases, both treated the same way:
 *      a) duplicate of a row already in the database -> rejected
 *      b) duplicate WITHIN the same file             -> FIRST WINS, every
 *         later record with that flightId is rejected
 *    Rejected duplicates appear in the report with reason "duplicate flightId".
 *    Rationale: reject-not-update is the safest default for an append-only
 *    feed; making it an upsert would silently change history.
 *
 * 4. AIRPORTS MUST EXIST AND BE OPEN, according to airport-service.
 *    Checked only after the record passes FlightValidator (no point looking
 *    up a blank code). Unknown or closed airport -> per-record rejection,
 *    exactly like rule 2. If airport-service itself is DOWN, that is not the
 *    record's fault: AirportServiceUnavailableException propagates, the
 *    transaction rolls back, and the caller gets HTTP 503.
 *
 * ===============================================================
 *
 * The whole ingest runs in ONE transaction, so a database-level failure rolls
 * the batch back rather than leaving a half-imported file behind.
 */
@Service
public class FlightService {

    private static final Logger log = LoggerFactory.getLogger(FlightService.class);

    private final FlightParser parser;
    private final FlightValidator validator;
    private final FlightRepository repository;
    private final AirportClient airportClient;

    public FlightService(FlightParser parser, FlightValidator validator, FlightRepository repository,
                         AirportClient airportClient) {
        this.parser = parser;
        this.validator = validator;
        this.repository = repository;
        this.airportClient = airportClient;
    }

    /**
     * Full ingestion flow for a JSON payload.
     *
     * @throws com.example.flights.parser.FlightParseException if the file is structurally invalid
     */
    @Transactional
    public IngestionReport ingest(InputStream json) {
        return ingestAll(parser.parse(json));
    }

    /** Same flow, reading the JSON straight off disk. */
    @Transactional
    public IngestionReport ingest(Path jsonFile) {
        return ingestAll(parser.parse(jsonFile));
    }

    private IngestionReport ingestAll(List<Flight> flights) {
        List<IngestionReport.RejectedRecord> rejected = new ArrayList<>();
        Set<String> seenInThisBatch = new HashSet<>();
        int imported = 0;

        for (Flight flight : flights) {
            List<String> reasons = new ArrayList<>();

            // Rule 2: per-record business validation, then Rule 4: airports.
            List<ValidationError> errors = validator.validate(flight);
            if (errors.isEmpty()) {
                errors = checkAirports(flight);
            }
            errors.stream()
                    .map(ValidationError::toString)
                    .forEach(reasons::add);

            // Rule 3: uniqueness, checked only once the id is known to be usable.
            String flightId = flight.flightId();
            if (flightId != null && !flightId.isBlank()) {
                if (!seenInThisBatch.add(flightId)) {
                    reasons.add("duplicate flightId within the same file (first record wins)");
                } else if (repository.existsById(flightId)) {
                    reasons.add("duplicate flightId: already present in the database");
                }
            }

            if (reasons.isEmpty()) {
                repository.save(flight);
                imported++;
            } else {
                log.warn("Rejected flight {}: {}", flightId, reasons);
                rejected.add(new IngestionReport.RejectedRecord(flightId, reasons));
            }
        }

        return new IngestionReport(flights.size(), imported, rejected.size(), rejected);
    }

    /**
     * Creates one flight (POST /flights). Unlike ingestion this is all-or-nothing:
     * a single bad record has no "good rows" to protect, so it throws.
     */
    @Transactional
    public Flight create(Flight flight) {
        List<ValidationError> errors = validator.validate(flight);
        if (errors.isEmpty()) {
            errors = checkAirports(flight);
        }
        if (!errors.isEmpty()) {
            throw new FlightValidationException(errors);
        }
        if (repository.existsById(flight.flightId())) {
            throw new DuplicateFlightException(flight.flightId());
        }
        repository.save(flight);
        return flight;
    }

    /**
     * Rule 4: both airports must be known to airport-service and still open.
     *
     * @throws com.example.flights.client.AirportServiceUnavailableException if airport-service cannot answer
     */
    private List<ValidationError> checkAirports(Flight flight) {
        List<ValidationError> errors = new ArrayList<>();
        checkAirport(errors, "departureAirport", flight.departureAirport());
        checkAirport(errors, "arrivalAirport", flight.arrivalAirport());
        return errors;
    }

    private void checkAirport(List<ValidationError> errors, String field, String iata) {
        Optional<Airport> airport = airportClient.findByIata(iata);
        if (airport.isEmpty()) {
            errors.add(new ValidationError(field, "unknown airport " + iata));
        } else if (!airport.get().active()) {
            errors.add(new ValidationError(field, "airport " + iata + " is closed"));
        }
    }

    public List<Flight> findAll() {
        return repository.findAll();
    }

    /** @throws FlightNotFoundException if there is no such flight */
    public Flight findById(String flightId) {
        Optional<Flight> flight = repository.findById(flightId);
        return flight.orElseThrow(() -> new FlightNotFoundException(flightId));
    }
}
