package com.example.flights.controller;

import com.example.flights.model.Flight;
import com.example.flights.model.IngestionReport;
import com.example.flights.service.FlightService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Flight REST API.
 *
 *   GET  /flights              200 - all flights
 *   GET  /flights/{flightId}   200 - one flight | 404 - unknown id
 *   POST /flights              201 + Location | 400 - invalid | 409 - duplicate
 *   POST /flights/import       200 + report   | 400 - unparseable file / no file
 *
 * The controller stays thin on purpose: it maps HTTP to service calls and
 * nothing else. All rules live in the validator and the service, and all error
 * translation lives in GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/flights")
public class FlightController {

    private final FlightService flightService;

    public FlightController(FlightService flightService) {
        this.flightService = flightService;
    }

    @GetMapping
    public List<Flight> getAllFlights() {
        return flightService.findAll();
    }

    @GetMapping("/{flightId}")
    public Flight getFlight(@PathVariable String flightId) {
        return flightService.findById(flightId);
    }

    @PostMapping
    public ResponseEntity<Flight> createFlight(@RequestBody Flight flight) {
        Flight saved = flightService.create(flight);
        return ResponseEntity
                .created(URI.create("/flights/" + saved.flightId()))
                .body(saved);
    }

    /**
     * Uploads a JSON file and runs the full ingestion flow.
     *
     * Returns 200 with an IngestionReport even when some records were rejected -
     * partial success is the documented policy. A structurally unparseable file
     * is a 400 and imports nothing.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngestionReport importFlights(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new EmptyImportFileException();
        }
        return flightService.ingest(file.getInputStream());
    }

    /** Uploaded file was present but had no content. Maps to HTTP 400. */
    public static class EmptyImportFileException extends RuntimeException {
        public EmptyImportFileException() {
            super("Uploaded file is empty");
        }
    }
}
