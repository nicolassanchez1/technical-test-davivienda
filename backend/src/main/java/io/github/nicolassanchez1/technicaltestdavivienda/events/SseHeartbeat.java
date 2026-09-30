package io.github.nicolassanchez1.technicaltestdavivienda.events;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps an idle stream alive.
 *
 * <p>A document can sit in {@code PROCESANDO} for longer than a proxy or a load balancer is willing
 * to hold a connection with no traffic on it, and the client would then be reconnecting instead of
 * waiting. A comment on every open stream is the cheapest thing that keeps the connection warm, and
 * it is also what reveals a reader that went away without the socket saying so: the write fails and
 * the registry forgets that stream.
 */
@Component
@Profile("!worker")
public class SseHeartbeat {

    private final SseEmitterRegistry emitters;

    public SseHeartbeat(SseEmitterRegistry emitters) {
        this.emitters = emitters;
    }

    @Scheduled(fixedRateString = "${app.sse-heartbeat-ms}", initialDelayString = "${app.sse-heartbeat-ms}")
    public void sendHeartbeat() {
        emitters.sendHeartbeat();
    }
}
