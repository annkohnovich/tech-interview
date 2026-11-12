package com.example.flights.model;

/**
 * A single business-validation failure: which field broke which rule.
 */
public record ValidationError(String field, String message) {

    @Override
    public String toString() {
        return field + ": " + message;
    }
}
