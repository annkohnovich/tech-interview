package com.example.flights.service;

import com.example.flights.model.ValidationError;

import java.util.List;

/** A single flight broke one or more business rules. Maps to HTTP 400. */
public class FlightValidationException extends RuntimeException {

    private final List<ValidationError> errors;

    public FlightValidationException(List<ValidationError> errors) {
        super("Flight failed validation: " + errors);
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> getErrors() {
        return errors;
    }
}
