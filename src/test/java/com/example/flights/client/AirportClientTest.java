package com.example.flights.client;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the HTTP client against a FAKE airport-service (WireMock).
 *
 * Mockito vs WireMock - the interview question:
 *   Mockito replaces a Java OBJECT. The HTTP call never happens, so URL
 *     building, JSON mapping, status codes and timeouts are NOT tested.
 *   WireMock replaces the other SERVICE. Real HTTP goes over a real socket,
 *     so all of the above IS tested - only the server on the other end is fake.
 *
 * Use Mockito one layer up (FlightServiceTest) and WireMock at the edge (here).
 *
 * No Spring context: the client is built with `new`, pointed at WireMock's port.
 */
class AirportClientTest {

    @RegisterExtension
    static WireMockExtension airportService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private AirportClient client;

    @BeforeEach
    void setUp() {
        client = new AirportClient(airportService.baseUrl(), Duration.ofMillis(500));
    }

    // ---------------------------------------------------------------
    // EXAMPLE 1 - 200: JSON is mapped, extra fields are ignored
    // ---------------------------------------------------------------
    @Test
    @DisplayName("200 -> airport is returned (city/country ignored)")
    void existingAirport_isMapped() {
        airportService.stubFor(get("/airports/WAW").willReturn(okJson("""
                {"iata":"WAW","name":"Warsaw Chopin","city":"Warsaw","country":"PL","active":true}
                """)));

        assertThat(client.findByIata("WAW"))
                .contains(new Airport("WAW", "Warsaw Chopin", true));

        // WireMock records every request - verify the client called the right URL.
        airportService.verify(1, getRequestedFor(urlEqualTo("/airports/WAW")));
    }

    // ---------------------------------------------------------------
    // EXAMPLE 2 - 404 means "does not exist", NOT an error
    // ---------------------------------------------------------------
    @Test
    @DisplayName("404 -> Optional.empty()")
    void unknownAirport_isEmpty() {
        airportService.stubFor(get("/airports/XYZ").willReturn(notFound()));

        assertThat(client.findByIata("XYZ")).isEmpty();
    }

    // ---------------------------------------------------------------
    // EXAMPLE 3 - 500 means "could not find out"
    // ---------------------------------------------------------------
    @Test
    @DisplayName("500 -> AirportServiceUnavailableException")
    void serverError_throws() {
        airportService.stubFor(get("/airports/WAW").willReturn(serverError()));

        assertThatThrownBy(() -> client.findByIata("WAW"))
                .isInstanceOf(AirportServiceUnavailableException.class)
                .hasMessageContaining("WAW");
    }

    // ---------------------------------------------------------------
    // EXAMPLE 4 - slow dependency: things Mockito simply cannot simulate
    // ---------------------------------------------------------------
    @Test
    @DisplayName("response slower than the 500ms timeout -> AirportServiceUnavailableException")
    void slowResponse_timesOut() {
        airportService.stubFor(get("/airports/WAW").willReturn(okJson("{}").withFixedDelay(1_500)));

        assertThatThrownBy(() -> client.findByIata("WAW"))
                .isInstanceOf(AirportServiceUnavailableException.class);
    }

    // ===============================================================
    // YOUR TURN (TRAINING.md, level 2)
    //
    // TODO 1  Network fault: willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
    // TODO 2  200 with a body that is not JSON ("<html>oops</html>") -> what SHOULD happen?
    //         Run it, see what does happen, decide if it is a bug.
    // TODO 3  A closed airport ("active": false) is returned, not treated as missing.
    // TODO 4  Scenarios: first call 500, second call 200 (WireMock "inScenario").
    //         Useful if you later add a retry to AirportClient.
    // ===============================================================
}
