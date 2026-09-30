package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;

/**
 * Raised once a document has been recorded as {@code ERROR} for a deterministic reason. The same
 * bytes would fail the same way on every delivery, so the inbound adapter turns this into whatever
 * its transport uses to stop redelivery instead of retrying the job.
 */
public final class NonRetryableProcessingException extends RuntimeException {

    private final DocumentErrorCode errorCode;

    public NonRetryableProcessingException(DocumentErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public DocumentErrorCode errorCode() {
        return errorCode;
    }
}
