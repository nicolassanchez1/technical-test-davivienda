package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;

/**
 * The rules an upload must satisfy, in the order they are applied. {@link UploadValidator} checks
 * everything that can be decided from the request alone; {@link #UNIQUE_CHECKSUM} needs the stored
 * checksums, so the use case applies it once the file has been read.
 */
public enum UploadRule {
    EXTENSION_ALLOWLIST(DocumentErrorCode.UNSUPPORTED_FORMAT),
    NON_EMPTY_FILE(DocumentErrorCode.EMPTY_CONTENT),
    MAXIMUM_FILE_SIZE(null),
    PDF_HEADER(DocumentErrorCode.CORRUPT_FILE),
    TEXT_WITHOUT_BINARY_BYTES(DocumentErrorCode.CORRUPT_FILE),
    UNIQUE_CHECKSUM(null);

    private final DocumentErrorCode errorCode;

    UploadRule(DocumentErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * The code the worker would have recorded for the same defect, or null when the rule is about
     * the request rather than the content: an oversized file is a limit, not a broken document,
     * and a file that is already stored is not broken at all.
     */
    public DocumentErrorCode errorCode() {
        return errorCode;
    }
}
