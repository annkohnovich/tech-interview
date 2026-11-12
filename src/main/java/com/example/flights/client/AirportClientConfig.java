package com.example.flights.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wires AirportClient from application.properties. Integration tests override
 * `airport-service.base-url` to point at WireMock instead of the real service.
 */
@Configuration
public class AirportClientConfig {

    @Bean
    public AirportClient airportClient(@Value("${airport-service.base-url}") String baseUrl,
                                       @Value("${airport-service.timeout}") Duration timeout) {
        return new AirportClient(baseUrl, timeout);
    }
}
