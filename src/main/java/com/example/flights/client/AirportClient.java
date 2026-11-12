package com.example.flights.client;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Optional;

/**
 * HTTP client for airport-service:  GET {baseUrl}/airports/{iata}
 *
 *   200          -> Optional.of(airport)
 *   404          -> Optional.empty()            (the airport does not exist)
 *   anything else, or a timeout / refused connection
 *                -> AirportServiceUnavailableException  (we could not find out)
 *
 * "Does not exist" and "could not find out" MUST stay different: the first is
 * the caller's data problem (400), the second is our infrastructure problem (503).
 *
 * Built with a plain constructor (base URL + timeout) rather than injected
 * Spring beans, so tests can point it at WireMock or a Pact mock server with
 * `new AirportClient(url, timeout)` - no Spring context needed.
 */
public class AirportClient {

    private final RestClient restClient;

    public AirportClient(String baseUrl, Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public Optional<Airport> findByIata(String iata) {
        try {
            Airport airport = restClient.get()
                    .uri("/airports/{iata}", iata)
                    .retrieve()
                    .body(Airport.class);
            return Optional.ofNullable(airport);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new AirportServiceUnavailableException(iata, e);
        }
    }
}
