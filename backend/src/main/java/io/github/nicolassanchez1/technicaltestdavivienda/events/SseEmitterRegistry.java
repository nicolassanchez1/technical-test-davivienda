package io.github.nicolassanchez1.technicaltestdavivienda.events;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure.DocumentStatusMessage;
import io.github.nicolassanchez1.technicaltestdavivienda.shared.config.AppProperties;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The browsers this api instance is streaming to, and the only place an event is written to them.
 *
 * <p>Two kinds of thread meet here: a status change arrives on a broker listener thread while
 * subscriptions and disconnections arrive on request threads, so the set is concurrent and the
 * iteration that fans an event out tolerates being mutated underneath it.
 *
 * <p>Registration is the only way in, and it is what attaches the callbacks that take the emitter
 * back out again. A registry that kept an emitter after its connection ended would hold the response
 * open, write to a dead socket on every event, and grow for as long as the process lives, so there
 * is deliberately no way to register a connection without arranging its removal.
 */
@Component
@Profile("!worker")
public class SseEmitterRegistry {

    /** The only event name on this stream; the payload says which document changed and how. */
    static final String STATUS_EVENT_NAME = "document.status";

    static final String HEARTBEAT_COMMENT = "heartbeat";

    /** Written on subscribe: the first byte is what flushes the response headers to the client. */
    private static final String STREAM_OPEN_COMMENT = "stream-open";

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    /** An emitter has no equality of its own, so membership is identity: one entry per connection. */
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    private final long timeoutMs;

    public SseEmitterRegistry(AppProperties properties) {
        this.timeoutMs = properties.sseTimeoutMs();
    }

    /** Opens a stream for one client, already wired to deregister itself however it ends. */
    public SseEmitter register() {
        return register(new SseEmitter(timeoutMs));
    }

    /**
     * Visible to the tests in this package so they can hand in an emitter whose container callbacks
     * they are able to fire. Production code reaches a registration only through {@link #register()}.
     */
    SseEmitter register(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> deregister(emitter));
        emitter.onTimeout(() -> {
            // A timed out stream is still open until someone closes it, and the browser reconnects
            // on its own once it does.
            deregister(emitter);
            emitter.complete();
        });
        emitter.onError(failure -> deregister(emitter));
        send(emitter, SseEmitter.event().comment(STREAM_OPEN_COMMENT));
        return emitter;
    }

    /**
     * Forwards one status change to every client connected to this instance.
     *
     * <p>The id is derived from the change itself rather than from a counter, so the same change
     * carries the same id on every connection. That is what lets a client recognise a repeat: the
     * broker redelivers, and a job that comes back for an already finished document announces it
     * again on purpose, to repair an announcement that was lost after its transaction committed.
     */
    public void broadcast(DocumentStatusMessage message) {
        String eventId = eventIdOf(message);
        log.debug("Forwarding {} for document {} to {} clients", message.status(), message.documentId(), count());
        forEachEmitter(emitter -> send(
                emitter,
                SseEmitter.event().id(eventId).name(STATUS_EVENT_NAME).data(message, MediaType.APPLICATION_JSON)));
    }

    /**
     * Writes a comment to every open stream. A comment rather than an event because an idle
     * connection only needs a byte to stay alive; anything else would be a status change that never
     * happened, and a client would have to filter it out.
     */
    public void sendHeartbeat() {
        forEachEmitter(emitter -> send(emitter, SseEmitter.event().comment(HEARTBEAT_COMMENT)));
    }

    /** How many clients this instance is streaming to. */
    public int count() {
        return emitters.size();
    }

    private void forEachEmitter(Consumer<SseEmitter> action) {
        for (SseEmitter emitter : emitters) {
            action.accept(emitter);
        }
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | RuntimeException failure) {
            // One reader whose browser went away must not cost the others their events, so the
            // failure ends that stream and the fan-out carries on.
            log.debug("Closing an event stream that could not be written to", failure);
            deregister(emitter);
            closeQuietly(emitter);
        }
    }

    private void deregister(SseEmitter emitter) {
        emitters.remove(emitter);
    }

    /**
     * Completes the stream rather than failing it. Failing it would dispatch the write error back
     * through the servlet, where the handler would try to answer a socket that is already gone with a
     * problem document, and log a routine disconnection as a fault of this service.
     */
    private static void closeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // The connection is already gone; there is no one left to report anything to.
        }
    }

    private static String eventIdOf(DocumentStatusMessage message) {
        return message.documentId() + "-" + message.occurredAt().toEpochMilli();
    }
}
