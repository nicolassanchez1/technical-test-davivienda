package io.github.nicolassanchez1.technicaltestdavivienda.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicolassanchez1.technicaltestdavivienda.support.AbstractIntegrationTest;
import io.github.nicolassanchez1.technicaltestdavivienda.support.SearchCorpus;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

/**
 * The ceiling, proved by lowering it until every search breaches it.
 *
 * <p>A statement the database cancels must reach the reader as 503 rather than as a 500: the query
 * was not wrong, it was too slow, and a client is right to retry a narrower one. The whole chain is
 * exercised here, from {@code set_config} through SQLSTATE 57014 to the problem document.
 */
// The web environment is restated because this annotation replaces the inherited one outright.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.search-timeout-ms=1")
class SearchTimeoutIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    private RestClient client;
    private SearchCorpus corpus;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        corpus = new SearchCorpus(jdbcClient);
        corpus.clear();
        corpus.fill(200, 10);
    }

    @AfterEach
    void tearDown() {
        corpus.clear();
    }

    @Test
    void answersServiceUnavailableWhenTheDatabaseCancelsTheSearch() {
        ResponseEntity<String> response = client.get()
                .uri("/api/search?q=mantenimiento")
                .exchange((request, raw) -> ResponseEntity.status(raw.getStatusCode())
                        .headers(raw.getHeaders())
                        .body(new String(raw.getBody().readAllBytes(), StandardCharsets.UTF_8)));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");
        assertThat(response.getBody())
                .contains("urn:problem-type:search-timeout")
                .contains("requestId");
    }

    /** The timeout is transaction local, so it must not survive onto the next use of the connection. */
    @Test
    void leavesTheConnectionWithoutTheLoweredTimeoutAfterwards() {
        client.get().uri("/api/search?q=mantenimiento").exchange((request, raw) -> raw.getStatusCode());

        String timeout =
                jdbcClient.sql("SHOW statement_timeout").query(String.class).single();

        assertThat(timeout).isEqualTo("0");
    }
}
