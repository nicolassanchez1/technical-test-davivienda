package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentStatusChanged;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * The body of a {@code documents.status} broadcast, and the shape an api instance forwards to its
 * SSE clients. The status travels as the Spanish wire value the whole contract is written in, and a
 * document that was indexed carries no error code at all rather than a null one.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentStatusMessage(
        UUID documentId, DocumentStatus status, DocumentErrorCode errorCode, Instant occurredAt) {

    public static DocumentStatusMessage of(DocumentStatusChanged event) {
        return new DocumentStatusMessage(event.documentId(), event.status(), event.errorCode(), event.occurredAt());
    }
}
