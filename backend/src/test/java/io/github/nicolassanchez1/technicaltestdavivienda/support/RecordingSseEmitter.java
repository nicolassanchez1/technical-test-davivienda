package io.github.nicolassanchez1.technicaltestdavivienda.support;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * An emitter that records what was written to it and lets a test play the part of the servlet
 * container: a real {@link SseEmitter} only runs its completion, timeout and error callbacks when an
 * async request ends, which no unit test has.
 */
public final class RecordingSseEmitter extends SseEmitter {

    private final List<String> frames = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile Runnable completionCallback = () -> {};
    private volatile Runnable timeoutCallback = () -> {};
    private volatile Consumer<Throwable> errorCallback = failure -> {};
    private volatile boolean writesFail;

    /** Makes every later write fail, as writing to a browser that has gone away does. */
    public void breakConnection() {
        writesFail = true;
    }

    public void fireCompletion() {
        completionCallback.run();
    }

    public void fireTimeout() {
        timeoutCallback.run();
    }

    public void fireError(Throwable failure) {
        errorCallback.accept(failure);
    }

    /** Everything written to this emitter, one entry per send, rendered as it goes on the wire. */
    public List<String> frames() {
        return List.copyOf(frames);
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void onCompletion(Runnable callback) {
        completionCallback = callback;
    }

    @Override
    public void onTimeout(Runnable callback) {
        timeoutCallback = callback;
    }

    @Override
    public void onError(Consumer<Throwable> callback) {
        errorCallback = callback;
    }

    @Override
    public void send(SseEventBuilder builder) throws IOException {
        if (writesFail) {
            throw new IOException("The client is gone");
        }
        frames.add(render(builder));
    }

    @Override
    public void complete() {
        closed.set(true);
    }

    /**
     * The data of a payload sent with a media type is still the object itself, because the converters
     * that would turn it into JSON belong to the running server.
     */
    private static String render(SseEventBuilder builder) {
        return builder.build().stream()
                .map(part -> String.valueOf(part.getData()))
                .collect(Collectors.joining());
    }
}
