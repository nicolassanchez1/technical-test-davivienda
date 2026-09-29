package io.github.nicolassanchez1.technicaltestdavivienda.documents.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Machine-readable reason a document could not be indexed. The frontend maps each code to
 * Spanish copy, so user-facing wording changes without touching the backend.
 */
public enum DocumentErrorCode {
    PDF_NO_TEXT_LAYER,
    UNSUPPORTED_FORMAT,
    CORRUPT_FILE,
    EMPTY_CONTENT,
    PROCESSING_FAILED;

    @JsonValue
    public String wireValue() {
        return name();
    }
}
