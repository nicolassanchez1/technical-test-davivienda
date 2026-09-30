package io.github.nicolassanchez1.technicaltestdavivienda.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentStatusChanged;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.github.nicolassanchez1.technicaltestdavivienda.support.AbstractIntegrationTest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The whole notification path, from the transaction that changed a document to the bytes a browser
 * reads: a real broker carries the event, a real HTTP connection receives it, and nothing in between
 * is stubbed. Mocking either end would leave the two hops that actually break, the fan-out
 * subscription and the flush to the socket, untested.
 *
 * <p>The heartbeat is shortened so an idle stream can be observed staying alive, and so a client that
 * walked away is noticed within the test: a closed socket only reveals itself on the next write.
 */
// The annotation replaces the inherited one outright, so the web environment is restated.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.sse-heartbeat-ms=400")
@Timeout(60)
class DocumentStatusStreamIT extends AbstractIntegrationTest {

    private static final Duration DELIVERED_WITHIN = Duration.ofSeconds(15);
    private static final Duration UNCOMMITTED_FOR = Duration.ofMillis(600);

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private SseEmitterRegistry emitters;

    @Autowired
    private ApplicationEventPublisher applicationEvents;

    @Autowired
    private TransactionTemplate transactions;

    /**
     * A closed connection is only noticed on the next write, so each test waits for the heartbeat that
     * lets its stream go. That leaves the next test a registry it can count on, and leaves the context
     * with no request still open when it shuts down.
     */
    @AfterEach
    void waitUntilEveryStreamIsReleased() {
        await().atMost(DELIVERED_WITHIN).until(() -> emitters.count() == 0);
    }

    @Test
    void answersWithAnEventStreamNoIntermediaryMayBufferOrCache() throws Exception {
        try (EventStream stream = subscribe()) {
            assertThat(stream.statusCode()).isEqualTo(200);
            assertThat(stream.header(HttpHeaders.CONTENT_TYPE)).hasValueSatisfying(contentType -> assertThat(
                            MediaType.parseMediaType(contentType).isCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                    .isTrue());
            assertThat(stream.header(HttpHeaders.CACHE_CONTROL)).hasValue("no-cache");
            // Without this one nginx buffers the stream and the news arrives in batches, or not at all.
            assertThat(stream.header("X-Accel-Buffering")).hasValue("no");
        }
    }

    @Test
    void deliversAnIndexedDocumentToAConnectedClient() throws Exception {
        UUID documentId = UUID.randomUUID();

        try (EventStream stream = subscribe()) {
            publishAfterCommit(DocumentStatusChanged.indexed(documentId));

            JsonNode payload = stream.awaitPayloadFor(documentId);
            assertThat(payload.path("status").asText()).isEqualTo(DocumentStatus.INDEXED.wireValue());
            assertThat(payload.path("occurredAt").asText()).isNotBlank();
            assertThat(payload.has("errorCode")).isFalse();
            assertThat(stream.lines()).contains("event:" + SseEmitterRegistry.STATUS_EVENT_NAME);
            // Every event is identified, so a client can tell two of them apart.
            assertThat(stream.lines()).anyMatch(line -> line.startsWith("id:") && line.contains(documentId.toString()));
        }
    }

    @Test
    void deliversAFailedDocumentWithTheReasonItFailed() throws Exception {
        UUID documentId = UUID.randomUUID();

        try (EventStream stream = subscribe()) {
            publishAfterCommit(DocumentStatusChanged.failed(documentId, DocumentErrorCode.PDF_NO_TEXT_LAYER));

            JsonNode payload = stream.awaitPayloadFor(documentId);
            assertThat(payload.path("status").asText()).isEqualTo(DocumentStatus.FAILED.wireValue());
            // A reader that stops waiting has to be told why, in a code the interface can translate.
            assertThat(payload.path("errorCode").asText()).isEqualTo(DocumentErrorCode.PDF_NO_TEXT_LAYER.name());
        }
    }

    @Test
    void reachesAClientThatSubscribedBeforeAndAfterAnEarlierChange() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        try (EventStream early = subscribe()) {
            publishAfterCommit(DocumentStatusChanged.indexed(first));
            early.awaitPayloadFor(first);

            try (EventStream late = subscribe()) {
                publishAfterCommit(DocumentStatusChanged.failed(second, DocumentErrorCode.EMPTY_CONTENT));

                assertThat(early.awaitPayloadFor(second).path("errorCode").asText())
                        .isEqualTo(DocumentErrorCode.EMPTY_CONTENT.name());
                assertThat(late.awaitPayloadFor(second).path("status").asText())
                        .isEqualTo(DocumentStatus.FAILED.wireValue());
                // The fan-out replays nothing: the client that arrived late never sees the first change.
                assertThat(late.payloadFor(first)).isEmpty();
            }
        }
    }

    @Test
    void saysNothingAboutAChangeItsTransactionHasNotCommitted() throws Exception {
        UUID documentId = UUID.randomUUID();

        try (EventStream stream = subscribe()) {
            transactions.executeWithoutResult(transaction -> {
                applicationEvents.publishEvent(DocumentStatusChanged.indexed(documentId));
                // A client told about a row that could still roll back would fetch a document that
                // is either absent or still PROCESANDO.
                await().pollDelay(UNCOMMITTED_FOR)
                        .atMost(DELIVERED_WITHIN)
                        .untilAsserted(() -> assertThat(stream.dataLines()).isEmpty());
            });

            assertThat(stream.awaitPayloadFor(documentId).path("status").asText())
                    .isEqualTo(DocumentStatus.INDEXED.wireValue());
        }
    }

    @Test
    void keepsAnIdleStreamAliveWithComments() throws Exception {
        try (EventStream stream = subscribe()) {
            await().atMost(DELIVERED_WITHIN)
                    .until(() -> stream.comments().contains(":" + SseEmitterRegistry.HEARTBEAT_COMMENT));

            // A heartbeat is a comment, so it never reaches an EventSource listener as an event.
            assertThat(stream.dataLines()).isEmpty();
        }
    }

    @Test
    void forgetsAClientThatDisconnects() throws Exception {
        EventStream stream = subscribe();
        await().atMost(DELIVERED_WITHIN).until(() -> emitters.count() == 1);

        stream.close();

        // The next heartbeat cannot be written, which is how a stream nobody reads is let go of.
        await().atMost(DELIVERED_WITHIN).until(() -> emitters.count() == 0);
    }

    private void publishAfterCommit(DocumentStatusChanged event) {
        // Exactly how the worker announces a status change: raised inside the transaction, published
        // by the after-commit listener.
        transactions.executeWithoutResult(transaction -> applicationEvents.publishEvent(event));
    }

    private EventStream subscribe() throws IOException, InterruptedException {
        return new EventStream(port);
    }

    /** A real Server-Sent Events client: one connection, read line by line as the bytes arrive. */
    private static final class EventStream implements AutoCloseable {

        private final HttpClient http = HttpClient.newHttpClient();
        private final HttpResponse<InputStream> response;
        private final List<String> lines = new CopyOnWriteArrayList<>();

        private EventStream(int port) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/events"))
                    .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .GET()
                    .build();
            response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            Thread.ofVirtual().name("event-stream-reader").start(this::readUntilClosed);
        }

        private void readUntilClosed() {
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isEmpty()) {
                        lines.add(line);
                    }
                }
            } catch (IOException endOfStream) {
                // The connection was closed, by this test or by the server shutting down.
            }
        }

        int statusCode() {
            return response.statusCode();
        }

        Optional<String> header(String name) {
            return response.headers().firstValue(name);
        }

        List<String> lines() {
            return List.copyOf(lines);
        }

        List<String> comments() {
            return linesStartingWith(":");
        }

        List<String> dataLines() {
            return linesStartingWith("data:");
        }

        private List<String> linesStartingWith(String prefix) {
            return lines().stream().filter(line -> line.startsWith(prefix)).toList();
        }

        JsonNode awaitPayloadFor(UUID documentId) {
            await().atMost(DELIVERED_WITHIN).until(() -> payloadFor(documentId).isPresent());
            return payloadFor(documentId).orElseThrow();
        }

        Optional<JsonNode> payloadFor(UUID documentId) {
            return dataLines().stream()
                    .map(line -> parse(line.substring("data:".length())))
                    .filter(payload -> documentId
                            .toString()
                            .equals(payload.path("documentId").asText()))
                    .findFirst();
        }

        private static JsonNode parse(String payload) {
            try {
                return JSON.readTree(payload);
            } catch (IOException malformed) {
                throw new UncheckedIOException("An event carried a payload that is not JSON: " + payload, malformed);
            }
        }

        @Override
        public void close() {
            try {
                response.body().close();
            } catch (IOException alreadyGone) {
                // Nothing to release: the stream had ended on its own.
            }
            http.shutdownNow();
        }
    }
}
