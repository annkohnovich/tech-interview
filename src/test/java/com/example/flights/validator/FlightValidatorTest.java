package com.example.flights.validator;

import com.example.flights.model.Flight;
import com.example.flights.model.FlightStatus;
import com.example.flights.model.ValidationError;
import jdk.jfr.Description;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Unit tests for FlightValidator.
 *
 * No Spring, no database, no mocks - the validator is a pure function:
 * a Flight goes in, a List<ValidationError> comes out.
 *
 * Every test starts from aValidFlight() and overrides exactly ONE field, so a
 * failure can only be caused by the rule under test.
 */
class FlightValidatorTest {

    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2026, 9, 8, 6, 15);
    private static final LocalDateTime ARRIVAL   = LocalDateTime.of(2026, 9, 8, 9, 45);

    private FlightValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FlightValidator();
    }

    // ---------------------------------------------------------------
    // EXAMPLE 1 - the happy path
    // ---------------------------------------------------------------
    @Test
    @DisplayName("a fully valid flight produces no errors")
    void validFlight_hasNoErrors() {
        // ARRANGE
        Flight flight = aValidFlight();

        // ACT
        List<ValidationError> errors = validator.validate(flight);

        // ASSERT
        assertThat(errors).isEmpty();
    }

    // ---------------------------------------------------------------
    // EXAMPLE 2 - exactly one broken rule
    // ---------------------------------------------------------------
    @Test
    @DisplayName("negative delayMinutes is rejected")
    void negativeDelay_isRejected() {
        // ARRANGE - valid in every way except the delay
        Flight flight = aValidFlight()
                .toBuilder()
                .delayMinutes(-30)
                .build();

        // ACT
        List<ValidationError> errors = validator.validate(flight);

        // ASSERT - the right field failed, for the right reason, and nothing else failed
        assertThat(errors)
                .extracting(ValidationError::field, ValidationError::message)
                .containsExactly(tuple("delayMinutes", "must not be negative"));
    }

    // ---------------------------------------------------------------
    // EXAMPLE 3 - overriding an existing Flight via toBuilder()
    // ---------------------------------------------------------------
    @Test
    @DisplayName("arrivalTime before departureTime is rejected")
    void arrivalBeforeDeparture_isRejected() {
        // ARRANGE - copy a built flight and change only the times
        Flight flight =
                aValidFlight()
                .toBuilder()
                .departureTime(LocalDateTime.of(2026, 9, 8, 10, 0))
                .arrivalTime(LocalDateTime.of(2026, 9, 8, 8, 0))
                .build();
        // ACT
        List<ValidationError> errors = validator.validate(flight);
        // ASSERT
        assertThat(errors)
                .extracting(ValidationError::field, ValidationError::message)
                .containsExactly(tuple("arrivalTime", "must be after departureTime"));
    }

    @Test
    @Description("")
    void airLineNul_isRejected() {
        //ARRANGE
        Flight flight =
                aValidFlight()
                        .toBuilder()
                        .airline(null)
                        .build();
        // ACT
        List<ValidationError> errors = validator.validate(flight);
        //ASSERT
        assertThat(errors)
                .extracting(ValidationError::field, ValidationError::message)
                .containsExactly( tuple("airline", "must not be null"));
    }

    // ===============================================================
    // TEST DATA
    // Returns Lombok's builder pre-filled with values that pass every rule,
    // so a test only has to state the field it wants to break.
    // ===============================================================
    private static Flight aValidFlight() {
        return Flight.builder()
                .flightId("FL-001")
                .flightNumber("BA123")
                .airline("British Airways")
                .departureAirport("LHR")
                .arrivalAirport("JFK")
                .departureTime(DEPARTURE)
                .arrivalTime(ARRIVAL)
                .delayMinutes(0)
                .status(FlightStatus.SCHEDULED)
                .build();
    }
}
