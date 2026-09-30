package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;

/**
 * Raised when a stored file cannot yield indexable text. Every case it reports is deterministic:
 * the same bytes fail the same way however often they are read, so the worker records {@link
 * #errorCode()} on the document and gives up instead of retrying. A transient failure, such as a
 * file the shared volume has not published yet, surfaces as an {@link java.io.UncheckedIOException}
 * so that it stays retryable.
 */
public final class ExtractionFailedException extends RuntimeException {

    private final DocumentErrorCode errorCode;

    public ExtractionFailedException(DocumentErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ExtractionFailedException(DocumentErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public DocumentErrorCode errorCode() {
        return errorCode;
    }
}
