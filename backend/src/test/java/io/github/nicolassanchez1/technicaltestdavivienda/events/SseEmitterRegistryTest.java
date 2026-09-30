package io.github.nicolassanchez1.technicaltestdavivienda.events;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure.DocumentStatusMessage;
import io.github.nicolassanchez1.technicaltestdavivienda.shared.config.AppProperties;
import io.github.nicolassanchez1.technicaltestdavivienda.support.RecordingSseEmitter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The registry is where a leak would live: an emitter kept after its connection ended holds the
 * response open and is written to on every event for as long as the process runs. Every way a
 * connection can end is therefore asserted to take it out again.
 */
class SseEmitterRegistryTest {

    private static final long STREAM_TIMEOUT_MS = 3_600_000L;
    private static final long HEARTBEAT_MS = 15_000L;

    private static final UUID DOCUMENT_ID = UUID.fromString("6f1b6a2c-8d43-4f91-9d5a-2b0f6c1e7a10");
    private static final Instant OCCURRED_AT = Instant.parse("2026-02-17T10:15:30.500Z");

    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry(properties());
    }

    @Test
    void countsOneConnectionPerConnectedClient() {
        registry.register(new RecordingSseEmitter());
        registry.register(new RecordingSseEmitter());

        assertThat(registry.count()).isEqualTo(2);
    }

    @Test
    void holdsTheStreamOpenForTheConfiguredTime() {
        assertThat(registry.register().getTimeout()).isEqualTo(STREAM_TIMEOUT_MS);
    }

    @Test
    void opensTheStreamWithACommentSoTheClientSeesTheResponseHeaders() {
        RecordingSseEmitter emitter = new RecordingSseEmitter();

        registry.register(emitter);

        assertThat(emitter.frames())
                .singleElement()
                .satisfies(frame -> assertThat(frame).startsWith(":").doesNotContain("event:"));
    }

    @Test
    void sendsAStatusChangeAsAnIdentifiedDocumentStatusEvent() {
        RecordingSseEmitter emitter = registered();

        registry.broadcast(indexed());

        assertThat(lastFrameOf(emitter))
                .contains("id:" + DOCUMENT_ID + "-" + OCCURRED_AT.toEpochMilli())
                .contains("event:" + SseEmitterRegistry.STATUS_EVENT_NAME)
                .contains("data:")
                .contains(DOCUMENT_ID.toString());
    }

    @Test
    void reachesEveryConnectedClientWithTheSameEvent() {
        List<RecordingSseEmitter> clients = List.of(registered(), registered(), registered());

        registry.broadcast(failed());

        assertThat(clients).allSatisfy(client -> assertThat(lastFrameOf(client))
                .isEqualTo(lastFrameOf(clients.getFirst()))
                .contains(DocumentErrorCode.PDF_NO_TEXT_LAYER.name()));
    }

    @Test
    void forgetsAConnectionTheClientCompleted() {
        RecordingSseEmitter emitter = registered();

        emitter.fireCompletion();

        assertThat(registry.count()).isZero();
    }

    @Test
    void forgetsAndClosesAConnectionThatTimedOut() {
        RecordingSseEmitter emitter = registered();

        emitter.fireTimeout();

        assertThat(registry.count()).isZero();
        // Left open, the request would hold a container thread and never reach the browser again.
        assertThat(emitter.isClosed()).isTrue();
    }

    @Test
    void forgetsAConnectionThatFailed() {
        RecordingSseEmitter emitter = registered();

        emitter.fireError(new IOException("broken pipe"));

        assertThat(registry.count()).isZero();
    }

    @Test
    void dropsTheClientItCannotWriteToAndStillServesTheOthers() {
        RecordingSseEmitter first = registered();
        RecordingSseEmitter gone = registered();
        RecordingSseEmitter last = registered();
        gone.breakConnection();

        registry.broadcast(indexed());

        assertThat(registry.count()).isEqualTo(2);
        assertThat(gone.isClosed()).isTrue();
        // The event is delivered to the client registered after the broken one, not only before it.
        assertThat(lastFrameOf(first)).contains(SseEmitterRegistry.STATUS_EVENT_NAME);
        assertThat(lastFrameOf(last)).contains(SseEmitterRegistry.STATUS_EVENT_NAME);
    }

    @Test
    void sendsTheHeartbeatAsACommentRatherThanAsAnEvent() {
        RecordingSseEmitter emitter = registered();

        registry.sendHeartbeat();

        // A client filters nothing out: a comment is invisible to an EventSource listener.
        assertThat(lastFrameOf(emitter))
                .isEqualTo(":" + SseEmitterRegistry.HEARTBEAT_COMMENT + "\n\n")
                .doesNotContain("event:");
    }

    @Test
    void forgetsAClientThatCannotTakeAHeartbeat() {
        RecordingSseEmitter emitter = registered();
        emitter.breakConnection();

        registry.sendHeartbeat();

        assertThat(registry.count()).isZero();
    }

    @Test
    void staysConsistentWhenConnectionsComeAndGoAcrossThreads() throws Exception {
        int threads = 8;
        int connectionsPerThread = 60;
        CountDownLatch ready = new CountDownLatch(1);
        List<Future<?>> runs = new ArrayList<>();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int thread = 0; thread < threads; thread++) {
                runs.add(pool.submit(() -> {
                    ready.await();
                    for (int connection = 0; connection < connectionsPerThread; connection++) {
                        RecordingSseEmitter emitter = new RecordingSseEmitter();
                        registry.register(emitter);
                        // Fan-out and heartbeat iterate the set while the other threads mutate it.
                        registry.broadcast(indexed());
                        registry.sendHeartbeat();
                        emitter.fireCompletion();
                    }
                    return null;
                }));
            }
            ready.countDown();
            for (Future<?> run : runs) {
                run.get(60, TimeUnit.SECONDS);
            }
        }

        assertThat(registry.count()).isZero();
    }

    private RecordingSseEmitter registered() {
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        registry.register(emitter);
        return emitter;
    }

    private static String lastFrameOf(RecordingSseEmitter emitter) {
        List<String> frames = emitter.frames();
        assertThat(frames).isNotEmpty();
        return frames.getLast();
    }

    private static DocumentStatusMessage indexed() {
        return new DocumentStatusMessage(DOCUMENT_ID, DocumentStatus.INDEXED, null, OCCURRED_AT);
    }

    private static DocumentStatusMessage failed() {
        return new DocumentStatusMessage(
                DOCUMENT_ID, DocumentStatus.FAILED, DocumentErrorCode.PDF_NO_TEXT_LAYER, OCCURRED_AT);
    }

    private static AppProperties properties() {
        return new AppProperties(
                Path.of("target", "storage"), 20, 10, 900L, 50, 4, 10, HEARTBEAT_MS, STREAM_TIMEOUT_MS);
    }
}
