package com.example.flights.service;

import com.example.flights.client.Airport;
import com.example.flights.client.AirportClient;
import com.example.flights.client.AirportServiceUnavailableException;
import com.example.flights.model.Flight;
import com.example.flights.model.FlightStatus;
import com.example.flights.model.IngestionReport;
import com.example.flights.model.ValidationError;
import com.example.flights.parser.FlightParser;
import com.example.flights.repository.FlightRepository;
import com.example.flights.validator.FlightValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * UNIT tests for FlightService, with Mockito.
 *
 * FlightService has four collaborators. We decide per collaborator:
 *   FlightValidator  -> REAL.  Pure function, fast, already tested on its own.
 *                               Mocking it would only re-state its rules here.
 *   FlightRepository -> MOCK.  It talks to a database.
 *   AirportClient    -> MOCK.  It talks to another microservice over HTTP.
 *   FlightParser     -> MOCK.  Lets an ingest test hand the service a ready-made
 *                               List<Flight> instead of writing a JSON file.
 *
 * Rule of thumb: mock what crosses a process boundary (DB, network, clock),
 * keep pure logic real.
 *
 * MockitoExtension runs in STRICT STUBS mode: a `when(...)` the test never
 * uses fails the test with UnnecessaryStubbingException. That keeps tests
 * honest - every stub must matter.
 */
@ExtendWith(MockitoExtension.class)
class FlightServiceTest {

    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2026, 9, 8, 6, 15);
    private static final LocalDateTime ARRIVAL   = LocalDateTime.of(2026, 9, 8, 9, 45);

    @Mock
    private FlightRepository repository;

    @Mock
    private AirportClient airportClient;

    @Mock
    private FlightParser parser;

    private FlightService service;

    @BeforeEach
    void setUp() {
        service = new FlightService(parser, new FlightValidator(), repository, airportClient);
    }

    // ---------------------------------------------------------------
    // EXAMPLE 1 - stub a dependency, verify a side effect
    // ---------------------------------------------------------------
    @Test
    @DisplayName("create: valid flight with open airports is saved")
    void create_validFlight_isSaved() {
        // ARRANGE - "whatever airport you are asked about, it exists and is open"
        when(airportClient.findByIata(anyString()))
                .thenAnswer(call -> Optional.of(openAirport(call.getArgument(0))));
        Flight flight = aValidFlight();

        // ACT
        Flight result = service.create(flight);

        // ASSERT - returned value AND the interaction with the mock
        assertThat(result).isEqualTo(flight);
        verify(repository).save(flight);
    }

    // ---------------------------------------------------------------
    // EXAMPLE 2 - different answers for different arguments + never()
    // ---------------------------------------------------------------
    @Test
    @DisplayName("create: unknown arrival airport is rejected and nothing is saved")
    void create_unknownArrivalAirport_isRejected() {
        // ARRANGE
        when(airportClient.findByIata("LHR")).thenReturn(Optional.of(openAirport("LHR")));
        when(airportClient.findByIata("JFK")).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThatThrownBy(() -> service.create(aValidFlight()))
                .isInstanceOf(FlightValidationException.class)
                .extracting(e -> ((FlightValidationException) e).getErrors())
                .asList()
                .containsExactly(new ValidationError("arrivalAirport", "unknown airport JFK"));

        verify(repository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // EXAMPLE 3 - make a mock THROW to simulate the other service being down
    // ---------------------------------------------------------------
    @Test
    @DisplayName("create: airport-service down -> exception propagates, DB untouched")
    void create_airportServiceDown_propagates() {
        // ARRANGE
        when(airportClient.findByIata(anyString()))
                .thenThrow(new AirportServiceUnavailableException("LHR", new RuntimeException("connection refused")));

        // ACT + ASSERT
        assertThatThrownBy(() -> service.create(aValidFlight()))
                .isInstanceOf(AirportServiceUnavailableException.class);

        // Not even existsById was called - the service stopped at the airport check.
        verifyNoInteractions(repository);
    }

    // ---------------------------------------------------------------
    // EXAMPLE 4 - prove a dependency is NOT called (short-circuit)
    // ---------------------------------------------------------------
    @Test
    @DisplayName("create: a flight that fails validation never reaches airport-service")
    void create_invalidFlight_doesNotCallAirportService() {
        Flight flight = aValidFlight().toBuilder().delayMinutes(-5).build();

        assertThatThrownBy(() -> service.create(flight))
                .isInstanceOf(FlightValidationException.class);

        verifyNoInteractions(airportClient);
    }

    // ---------------------------------------------------------------
    // EXAMPLE 5 - ArgumentCaptor: inspect WHAT was passed to a mock
    // ---------------------------------------------------------------
    @Test
    @DisplayName("ingest: flight from a closed airport is rejected, the other is saved")
    void ingest_closedAirport_isRejectedOthersSaved() {
        // ARRANGE - parser mock returns two flights; TXL is closed
        Flight good = aValidFlight();
        Flight fromTegel = aValidFlight().toBuilder().flightId("FL-002").departureAirport("TXL").build();
        when(parser.parse(any(InputStream.class))).thenReturn(List.of(good, fromTegel));

        when(airportClient.findByIata(anyString()))
                .thenAnswer(call -> Optional.of(openAirport(call.getArgument(0))));
        when(airportClient.findByIata("TXL"))
                .thenReturn(Optional.of(new Airport("TXL", "Berlin Tegel", false)));

        // ACT
        IngestionReport report = service.ingest(InputStream.nullInputStream());

        // ASSERT - the report
        assertThat(report.imported()).isEqualTo(1);
        assertThat(report.rejectedRecords())
                .extracting(IngestionReport.RejectedRecord::flightId, IngestionReport.RejectedRecord::reasons)
                .containsExactly(tuple("FL-002", List.of("departureAirport: airport TXL is closed")));

        // ASSERT - exactly the good flight reached the repository
        ArgumentCaptor<Flight> saved = ArgumentCaptor.forClass(Flight.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().flightId()).isEqualTo("FL-001");
    }

    // ===============================================================
    // YOUR TURN - write these (see TRAINING.md, level 1)
    //
    // TODO 1  create: repository.existsById returns true -> DuplicateFlightException,
    //         save() never called.
    // TODO 2  findById: repository returns Optional.empty() -> FlightNotFoundException.
    // TODO 3  ingest: the same flightId twice in one batch -> first saved, second
    //         rejected with "duplicate flightId within the same file (first record wins)".
    //         Hint: verify(repository, times(1)).save(any()).
    // TODO 4  ingest: airport-service throws on the 2nd flight -> exception propagates.
    //         Hint: thenReturn(...).thenThrow(...) chains answers for consecutive calls.
    // TODO 5  Use InOrder to prove existsById is checked BEFORE save.
    // ===============================================================

    private static Airport openAirport(String iata) {
        return new Airport(iata, "Airport " + iata, true);
    }

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
