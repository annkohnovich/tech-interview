package com.example.airports;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 *   GET /airports          200 - all airports
 *   GET /airports/{iata}   200 - one airport | 404 - unknown code
 *
 * This response shape is what flight-service's Pact contract pins down.
 * Rename a field here and the provider verification test goes red.
 */
@RestController
@RequestMapping("/airports")
public class AirportController {

    private final AirportRepository repository;

    public AirportController(AirportRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Airport> all() {
        return repository.findAll();
    }

    @GetMapping("/{iata}")
    public ResponseEntity<?> byIata(@PathVariable String iata) {
        return repository.findByIata(iata)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("status", 404, "message", "Airport not found: " + iata)));
    }
}
