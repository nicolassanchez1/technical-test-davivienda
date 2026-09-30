package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised inside the transaction that moved a document out of {@code PROCESANDO}. As on the upload
 * side, it only records what happened: {@link StatusEventDispatcher} is what turns it into a
 * broadcast, and only once the transaction has committed.
 *
 * @param errorCode why the document failed, and null for a document that was indexed
 */
public record DocumentStatusChanged(
        UUID documentId, DocumentStatus status, DocumentErrorCode errorCode, Instant occurredAt) {

    public DocumentStatusChanged {
        // The same invariant the documents table enforces: a failure says why, and nothing else does.
        if ((status == DocumentStatus.FAILED) != (errorCode != null)) {
            throw new IllegalArgumentException("A failed document carries an error code, and only a failed one does.");
        }
    }

    public static DocumentStatusChanged indexed(UUID documentId) {
        return new DocumentStatusChanged(documentId, DocumentStatus.INDEXED, null, Instant.now());
    }

    public static DocumentStatusChanged failed(UUID documentId, DocumentErrorCode errorCode) {
        return new DocumentStatusChanged(documentId, DocumentStatus.FAILED, errorCode, Instant.now());
    }
}
