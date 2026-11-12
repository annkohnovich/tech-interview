package com.example.flights.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.example.flights.client.Airport;
import com.example.flights.client.AirportClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CONSUMER-DRIVEN CONTRACT test - the consumer half.
 *
 * flight-service (consumer) writes down what it NEEDS from airport-service
 * (provider). Running this test:
 *   1. starts a Pact mock server that answers exactly as declared below,
 *   2. runs the REAL AirportClient against it,
 *   3. if the client behaved, writes pacts/flight-service-airport-service.json.
 *
 * That JSON file is the contract. airport-service's AirportProviderPactTest
 * replays it against the real provider. If the provider renames `active` to
 * `isOpen`, the PROVIDER build goes red - before anything is deployed.
 *
 * WireMock vs Pact: a WireMock stub is your private guess about the other
 * service and can silently drift from reality. A pact is shared and verified
 * on both sides, so it cannot drift.
 *
 * Matchers: stringType("name", ...) means "any string" - the provider may
 * rename "Warsaw Chopin" without breaking us. stringValue("iata", "WAW") means
 * exactly that value. Be as loose as the consumer can afford.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "airport-service")
class AirportClientPactTest {

    // ---------------------------------------------------------------
    // INTERACTION 1 - airport exists
    // ---------------------------------------------------------------
    @Pact(consumer = "flight-service")
    V4Pact existingAirport(PactDslWithProvider builder) {
        return builder
                .given("airport WAW exists and is active")        // provider state
                .uponReceiving("a request for an existing airport")
                    .path("/airports/WAW")
                    .method("GET")
                .willRespondWith()
                    .status(200)
                    .body(new PactDslJsonBody()
                            .stringValue("iata", "WAW")
                            .stringType("name", "Warsaw Chopin")
                            .booleanType("active", true))
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "existingAirport")
    @DisplayName("contract: existing airport is returned")
    void existingAirport(MockServer mockServer) {
        AirportClient client = new AirportClient(mockServer.getUrl(), Duration.ofSeconds(2));

        assertThat(client.findByIata("WAW"))
                .contains(new Airport("WAW", "Warsaw Chopin", true));
    }

    // ---------------------------------------------------------------
    // INTERACTION 2 - airport does not exist
    // ---------------------------------------------------------------
    @Pact(consumer = "flight-service")
    V4Pact unknownAirport(PactDslWithProvider builder) {
        return builder
                .given("airport XYZ does not exist")
                .uponReceiving("a request for an unknown airport")
                    .path("/airports/XYZ")
                    .method("GET")
                .willRespondWith()
                    .status(404)
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "unknownAirport")
    @DisplayName("contract: unknown airport is 404")
    void unknownAirport(MockServer mockServer) {
        AirportClient client = new AirportClient(mockServer.getUrl(), Duration.ofSeconds(2));

        assertThat(client.findByIata("XYZ")).isEmpty();
    }

    // ===============================================================
    // YOUR TURN (TRAINING.md, level 4)
    //
    // TODO 1  Add interaction 3: "airport TXL exists and is closed" -> active=false.
    //         Then add the matching @State in airport-service and run its provider test.
    // TODO 2  Break it on purpose: in airport-service rename Airport.active to isOpen.
    //         Run `mvn test` in airport-service and read the failure.
    // TODO 3  Change stringType("name") to stringValue("name", "Warsaw")
    //         - the provider test fails. Why? What does that teach about matchers?
    // ===============================================================
}
