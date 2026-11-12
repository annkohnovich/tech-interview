package com.example.flights.controller;

import com.example.flights.client.AirportServiceUnavailableException;
import com.example.flights.service.FlightNotFoundException;
import com.example.flights.service.FlightService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SLICE test: only the web layer is started (controller + exception handler +
 * JSON). No database, no AirportClient, no real HTTP port - MockMvc calls the
 * controller in-process.
 *
 * @MockBean puts a Mockito mock INTO the Spring context in place of the real
 * FlightService. (Spring Boot 3.4+ renames it @MockitoBean.)
 *
 * What this level proves: "exception X becomes HTTP status Y with body Z".
 * Cheaper than a full @SpringBootTest, more realistic than a plain unit test.
 */
@WebMvcTest(FlightController.class)
class FlightControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FlightService flightService;

    // ---------------------------------------------------------------
    // EXAMPLE 1 - domain exception -> 404 + error body
    // ---------------------------------------------------------------
    @Test
    @DisplayName("GET /flights/{id}: unknown id -> 404 with error body")
    void unknownFlight_is404() throws Exception {
        given(flightService.findById("NOPE")).willThrow(new FlightNotFoundException("NOPE"));

        mockMvc.perform(get("/flights/NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Flight not found: NOPE"));
    }

    // ---------------------------------------------------------------
    // EXAMPLE 2 - downstream failure -> 503
    // ---------------------------------------------------------------
    @Test
    @DisplayName("POST /flights: airport-service down -> 503")
    void airportServiceDown_is503() throws Exception {
        given(flightService.create(any()))
                .willThrow(new AirportServiceUnavailableException("LHR", new RuntimeException("timeout")));

        mockMvc.perform(post("/flights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_FLIGHT_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Service Unavailable"));
    }

    // ===============================================================
    // YOUR TURN (TRAINING.md, level 3)
    //
    // TODO 1  POST /flights with "{ not json" -> 400 "Malformed Request Body".
    //         Does flightService get called at all? verifyNoInteractions(flightService).
    // TODO 2  POST /flights/import with an EMPTY file part -> 400.
    //         Hint: multipart("/flights/import").file(new MockMultipartFile("file", new byte[0]))
    // TODO 3  POST /flights happy path -> 201 and a Location header "/flights/FL-001".
    // ===============================================================

    private static final String VALID_FLIGHT_JSON = """
            {
              "flightId": "FL-001",
              "flightNumber": "BA123",
              "airline": "British Airways",
              "departureAirport": "LHR",
              "arrivalAirport": "JFK",
              "departureTime": "2026-09-08T06:15:00",
              "arrivalTime": "2026-09-08T09:45:00",
              "delayMinutes": 0,
              "status": "SCHEDULED"
            }
            """;
}
