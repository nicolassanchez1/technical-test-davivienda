package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;

/** The rules an upload must satisfy, in the order the validator applies them. */
public enum UploadRule {
    EXTENSION_ALLOWLIST(DocumentErrorCode.UNSUPPORTED_FORMAT),
    NON_EMPTY_FILE(DocumentErrorCode.EMPTY_CONTENT),
    MAXIMUM_FILE_SIZE(null),
    PDF_HEADER(DocumentErrorCode.CORRUPT_FILE),
    TEXT_WITHOUT_BINARY_BYTES(DocumentErrorCode.CORRUPT_FILE);

    private final DocumentErrorCode errorCode;

    UploadRule(DocumentErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * The code the worker would have recorded for the same defect, or null when the rule is about
     * the request rather than the content: an oversized file is a limit, not a broken document.
     */
    public DocumentErrorCode errorCode() {
        return errorCode;
    }
}
