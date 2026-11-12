package com.example.airports;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.spring.junit5.PactVerificationSpringProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * CONSUMER-DRIVEN CONTRACT test - the provider half.
 *
 * Reads every pact in ../pacts where provider = "airport-service", starts this
 * service for real, and replays each recorded request against it. The test
 * passes only if every real response satisfies what the consumer said it needs.
 *
 * Run order matters: flight-service's AirportClientPactTest must have run
 * first to WRITE the pact file (in CI that is an earlier stage; in real teams
 * the file goes through a Pact Broker instead of a shared folder).
 *
 * @State methods put the provider into the situation each interaction assumes.
 * The string must match `.given(...)` in the consumer test character for character.
 */
@Provider("airport-service")
@PactFolder("../pacts")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AirportProviderPactTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AirportRepository repository;

    @BeforeEach
    void pointPactAtThisService(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
    }

    @TestTemplate
    @ExtendWith(PactVerificationSpringProvider.class)
    void verifyContract(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("airport WAW exists and is active")
    void wawExists() {
        repository.save(new Airport("WAW", "Warsaw Chopin", "Warsaw", "PL", true));
    }

    @State("airport XYZ does not exist")
    void xyzDoesNotExist() {
        repository.delete("XYZ");
    }
}
