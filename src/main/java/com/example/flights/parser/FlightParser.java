package com.example.flights.parser;

import com.example.flights.model.Flight;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Turns a JSON array into Flight objects. Nothing else.
 *
 * WHAT THIS LAYER IS RESPONSIBLE FOR (parsing / deserialization concerns):
 *   - malformed JSON syntax
 *   - wrong JSON types            ("delayMinutes": "abc")
 *   - unparseable date strings    ("2026-13-45T10:00:00")
 *   - unknown enum values         ("status": "FLYING")
 *
 * WHAT THIS LAYER IGNORES (business concerns - see FlightValidator):
 *   - missing fields  -> parsed as null
 *   - blank fields    -> parsed as ""
 *   - negative delay  -> parsed as -30
 *   - duplicates, departure == arrival, arrival before departure
 *
 * POLICY: parsing is FAIL-FAST for the whole file. If any single record is
 * structurally broken the entire batch is rejected, because a structurally
 * broken file usually means a bad producer rather than one bad row.
 *
 * The ObjectMapper is built here rather than injected so the parser can be unit
 * tested with a plain `new FlightParser()` and no Spring context.
 */
@Component
public class FlightParser {

    private static final TypeReference<List<Flight>> FLIGHT_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public FlightParser() {
        this.objectMapper = new ObjectMapper()
                // LocalDateTime support: ISO-8601 strings such as 2026-09-08T06:15:00
                .registerModule(new JavaTimeModule())
                // Lenient: unexpected extra fields in the feed do not break ingestion.
                // Flip this to strict and it becomes a parsing failure - a good test scenario.
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /** Parses a JSON array of flights from a stream. */
    public List<Flight> parse(InputStream json) {
        try {
            return objectMapper.readValue(json, FLIGHT_LIST);
        } catch (IOException e) {
            throw new FlightParseException("Unable to parse flight JSON: " + e.getMessage(), e);
        }
    }

    /** Parses a JSON array of flights from a raw string. */
    public List<Flight> parse(String json) {
        try {
            return objectMapper.readValue(json, FLIGHT_LIST);
        } catch (IOException e) {
            throw new FlightParseException("Unable to parse flight JSON: " + e.getMessage(), e);
        }
    }

    /** Parses a JSON array of flights straight off disk. */
    public List<Flight> parse(Path jsonFile) {
        try (InputStream in = Files.newInputStream(jsonFile)) {
            return parse(in);
        } catch (IOException e) {
            throw new FlightParseException("Unable to read flight file: " + jsonFile, e);
        }
    }
}
