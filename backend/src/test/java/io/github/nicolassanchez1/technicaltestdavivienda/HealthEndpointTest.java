package io.github.nicolassanchez1.technicaltestdavivienda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Exercises the running servlet container because the context path and the Actuator base
 * path together decide the public health URL; a slice test would not catch a mismatch.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTest {

    @LocalServerPort
    private int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    @Test
    void reportsApplicationHealthUnderTheApiPrefix() {
        String body = client().get().uri("/api/health").retrieve().body(String.class);

        assertThat(body).contains("\"status\":\"UP\"");
    }

    @Test
    void doesNotServeHealthOutsideTheApiPrefix() {
        assertThatThrownBy(() -> client().get().uri("/health").retrieve().body(String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .extracting(error -> ((HttpClientErrorException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void publishesAnOpenApiDocument() {
        String body = client().get().uri("/api/v3/api-docs").retrieve().body(String.class);

        assertThat(body).contains("\"openapi\"");
    }
}
