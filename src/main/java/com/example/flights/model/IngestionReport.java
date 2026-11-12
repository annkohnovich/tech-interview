package com.example.flights.model;

import java.util.List;

/**
 * Result of an ingestion run.
 *
 * Ingestion is PARTIALLY SUCCESSFUL by design: valid records are persisted,
 * invalid ones are collected here and reported back to the caller.
 */
public record IngestionReport (
        int totalRecords,
        int imported,
        int rejected,
        List<RejectedRecord> rejectedRecords
) {

    /** One rejected record and every reason it was rejected. */
    public record RejectedRecord(String flightId, List<String> reasons) {
    }
}
