package com.example.flights.api;

import com.example.flights.repository.FlightRepository;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.qameta.allure.Feature;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

/**
 * INTEGRATION / API test: the whole flight-service, for real.
 *
 *   REST Assured --HTTP--> flight-service (random port) --SQL--> H2
 *                                 |
 *                                 +--HTTP--> WireMock (pretending to be airport-service)
 *
 * Real:  HTTP, JSON, controller, exception handler, service, validator,
 *        transactions, SQL, the AirportClient's HTTP code.
 * Fake:  only the OTHER microservice. We control it, so we can make it say
 *        "unknown airport" or "500" or "slow" on demand - things you could
 *        never reliably trigger against a shared test environment.
 *
 * Each test asserts at THREE levels:
 *   1. the HTTP response (REST Assured)
 *   2. the database state (plain SQL via JdbcTemplate)
 *   3. what we sent to the dependency (WireMock verify)
 *
 * Named *IT, so it runs under Failsafe: `mvn verify`, not `mvn test`.
 */
@Feature("Flight API")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlightApiIT {

    @RegisterExtension
    static WireMockExtension airportService =
            WireMockExtension
                    .newInstance()
                    .options(wireMockConfig().dynamicPort())
            .build();

    /** Point the app's AirportClient at WireMock instead of localhost:8081. */
    @DynamicPropertySource
    static void airportServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("airport-service.base-url", airportService::baseUrl);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private FlightRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private RequestSpecification api;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        // Reusable request spec: port, JSON, and every request/response attached to Allure.
        api = new RequestSpecBuilder()
                .setPort(port)
                .setContentType(ContentType.JSON)
                .addFilter(new AllureRestAssured())
                .build();

        // Default world: every airport exists and is open. Response templating
        // echoes the requested code back, so /airports/WAW answers "iata":"WAW".
        // Individual tests add more specific stubs on top (newest stub wins).
        airportService.stubFor(get(urlPathMatching("/airports/[A-Z]{3}"))
                .willReturn(okJson("""
                        {"iata":"{{request.path.[1]}}","name":"Test airport {{request.path.[1]}}",
                         "city":"Testville","country":"PL","active":true}
                        """).withTransformers("response-template")));
    }

    // ---------------------------------------------------------------
    // EXAMPLE 1 - happy path, end to end
    // ---------------------------------------------------------------
    @Test
    @DisplayName("POST /flights -> 201, readable via GET, row in DB, both airports looked up")
    void createFlight_happyPath() {
        // ACT + ASSERT (HTTP)
        given(api)
                .body(flightJson("FL-001", "WAW", "LHR"))
        .when()
                .post("/flights")
        .then()
                .statusCode(201)
                .header("Location", endsWith("/flights/FL-001"))
                .body("flightId", equalTo("FL-001"));

        given(api)
        .when()
                .get("/flights/FL-001")
        .then()
                .statusCode(200)
                .body("departureAirport", equalTo("WAW"))
                .body("departureTime", equalTo("2026-09-08T06:15:00"));

        // ASSERT (DB) - plain SQL, independent of the code under test
        String status = jdbc.queryForObject(
                "SELECT status FROM flights WHERE flight_id = ?", String.class, "FL-001");
        assertThat(status).isEqualTo("SCHEDULED");

        // ASSERT (dependency) - we really called airport-service, once per airport
        airportService.verify(1, getRequestedFor(urlPathMatching("/airports/WAW")));
        airportService.verify(1, getRequestedFor(urlPathMatching("/airports/LHR")));
    }

    // ---------------------------------------------------------------
    // EXAMPLE 2 - dependency says "no such airport"
    // ---------------------------------------------------------------
    @Test
    @DisplayName("POST /flights with unknown airport -> 400, nothing in DB")
    void createFlight_unknownAirport_is400() {
        airportService.stubFor(get("/airports/XYZ").willReturn(notFound()));

        given(api)
                .body(flightJson("FL-002", "WAW", "XYZ"))
        .when()
                .post("/flights")
        .then()
                .statusCode(400)
                .body("error", equalTo("Validation Failed"))
                .body("details", hasItem("arrivalAirport: unknown airport XYZ"));

        assertThat(countFlights()).isZero();
    }

    // ---------------------------------------------------------------
    // EXAMPLE 3 - dependency is DOWN
    // ---------------------------------------------------------------
    @Test
    @DisplayName("POST /flights while airport-service returns 500 -> 503, nothing in DB")
    void createFlight_airportServiceDown_is503() {
        airportService.stubFor(get(urlPathMatching("/airports/.*")).willReturn(serverError()));

        given(api)
                .body(flightJson("FL-003", "WAW", "LHR"))
        .when()
                .post("/flights")
        .then()
                .statusCode(503)
                .body("message", containsString("Airport service unavailable"));

        assertThat(countFlights()).isZero();
    }

    // ---------------------------------------------------------------
    // EXAMPLE 4 - file upload (multipart), the real ingestion flow
    // ---------------------------------------------------------------
    @Test
    @DisplayName("POST /flights/import flights.json -> 8 imported, 8 rows in DB")
    void importFile_happyPath() {
        given(api)
                .contentType(ContentType.MULTIPART)
                .multiPart("file", new File("src/test/resources/testdata/flights.json"), "application/json")
        .when()
                .post("/flights/import")
        .then()
                .statusCode(200)
                .body("totalRecords", equalTo(8))
                .body("imported", equalTo(8))
                .body("rejected", equalTo(0));

        assertThat(countFlights()).isEqualTo(8);
    }

    // ===============================================================
    // YOUR TURN (TRAINING.md, level 5)
    //
    // TODO 1  Import flights-mixed.json -> 200, imported 2, rejected 1; check with
    //         SQL that the rejected flightId is NOT in the table.
    // TODO 2  Import flights.json while airport-service is down -> 503 and
    //         ZERO rows (proves the transaction rolled back).
    //         Bonus: let the first 3 airport calls succeed and only then fail
    //         (WireMock scenarios) - still zero rows?
    // TODO 3  Closed airport: stub /airports/TXL with "active": false -> 400,
    //         details contain "airport TXL is closed".
    // TODO 4  POST the same flight twice -> second is 409.
    // TODO 5  Slow airport-service: withFixedDelay(3000) -> 503.
    //         (Timeout is 2s; override "airport-service.timeout" to 300ms in
    //         @DynamicPropertySource to keep the suite fast.)
    // TODO 6  Extract a reusable FlightApiClient / POJO for request bodies
    //         instead of JSON strings - the "framework" part of the job ad.
    // ===============================================================

    private long countFlights() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM flights", Long.class);
    }

    private static String flightJson(String flightId, String from, String to) {
        return """
                {
                  "flightId": "%s",
                  "flightNumber": "LO123",
                  "airline": "LOT",
                  "departureAirport": "%s",
                  "arrivalAirport": "%s",
                  "departureTime": "2026-09-08T06:15:00",
                  "arrivalTime": "2026-09-08T08:30:00",
                  "delayMinutes": 0,
                  "status": "SCHEDULED"
                }
                """.formatted(flightId, from, to);
    }
}
