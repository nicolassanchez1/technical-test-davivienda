package io.github.nicolassanchez1.technicaltestdavivienda.events.web;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure.DocumentStatusMessage;
import io.github.nicolassanchez1.technicaltestdavivienda.events.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The stream a client listens on to learn that a document finished indexing or failed.
 *
 * <p>One connection carries every document: a client opens this once when the application starts,
 * and nothing here is ever asked again for an answer it already gave.
 */
// This controller carries a profile, where the others do not, because it is useless without the
// registry of open streams, and only an api instance holds one.
@RestController
@Profile("!worker")
@RequestMapping("/events")
public class EventsController {

    /**
     * Turns off response buffering in nginx. Without it a proxy is free to hold the stream until its
     * buffer fills, which for a feed of small events means the news arrives late or not at all.
     */
    private static final String ACCEL_BUFFERING_HEADER = "X-Accel-Buffering";

    private static final String BUFFERING_DISABLED = "no";

    private final SseEmitterRegistry emitters;

    public EventsController(SseEmitterRegistry emitters) {
        this.emitters = emitters;
    }

    @Operation(
            summary = "Subscribe to document status changes",
            description = "A Server-Sent Events stream. Every change is an event named `document.status` carrying an "
                    + "id of its own, and a comment is written periodically so an idle connection stays open. The "
                    + "stream is opened once per client, never once per document, and it is never asked for a status "
                    + "it has not announced.")
    @ApiResponse(
            responseCode = "200",
            description = "An open event stream. Each `document.status` event carries this payload.",
            content =
                    @Content(
                            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                            schema = @Schema(implementation = DocumentStatusMessage.class)))
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header(ACCEL_BUFFERING_HEADER, BUFFERING_DISABLED)
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitters.register());
    }
}
