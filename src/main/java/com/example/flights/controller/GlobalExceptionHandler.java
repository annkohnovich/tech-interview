package com.example.flights.controller;

import com.example.flights.client.AirportServiceUnavailableException;
import com.example.flights.model.ValidationError;
import com.example.flights.parser.FlightParseException;
import com.example.flights.service.DuplicateFlightException;
import com.example.flights.service.FlightNotFoundException;
import com.example.flights.service.FlightValidationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Single place where domain failures become HTTP status codes.
 *
 *   FlightValidationException  -> 400  (business rules broken)
 *   FlightParseException       -> 400  (file structurally invalid)
 *   HttpMessageNotReadable     -> 400  (request body structurally invalid)
 *   Missing request part/param -> 400  (no `file` part on /import)
 *   EmptyImportFileException   -> 400  (file part present but empty)
 *   FlightNotFoundException    -> 404
 *   DuplicateFlightException   -> 409  (service-level uniqueness check)
 *   DuplicateKeyException      -> 409  (database primary key, the safety net)
 *   AirportServiceUnavailable  -> 503  (downstream dependency failed)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FlightValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(FlightValidationException ex) {
        List<String> details = ex.getErrors().stream().map(ValidationError::toString).toList();
        return status(HttpStatus.BAD_REQUEST, "Validation Failed", "Flight failed validation", details);
    }

    @ExceptionHandler(FlightParseException.class)
    public ResponseEntity<ErrorResponse> handleParse(FlightParseException ex) {
        return status(HttpStatus.BAD_REQUEST, "Parse Failed", ex.getMessage(), List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return status(HttpStatus.BAD_REQUEST, "Malformed Request Body",
                "Request body could not be parsed", List.of());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            FlightController.EmptyImportFileException.class})
    public ResponseEntity<ErrorResponse> handleBadImportRequest(Exception ex) {
        return status(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), List.of());
    }

    @ExceptionHandler(FlightNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(FlightNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), List.of());
    }

    @ExceptionHandler({DuplicateFlightException.class, DuplicateKeyException.class})
    public ResponseEntity<ErrorResponse> handleDuplicate(Exception ex) {
        return status(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), List.of());
    }

    @ExceptionHandler(AirportServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAirportServiceDown(AirportServiceUnavailableException ex) {
        return status(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", ex.getMessage(), List.of());
    }

    private ResponseEntity<ErrorResponse> status(HttpStatus status, String error,
                                                 String message, List<String> details) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), error, message, details));
    }
}
