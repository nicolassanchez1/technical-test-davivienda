package io.github.nicolassanchez1.technicaltestdavivienda.documents.domain;

import java.util.UUID;

/**
 * Raised when a file whose checksum is already stored is uploaded again. It carries the id of the
 * document that holds the content so the caller can point at it instead of storing it twice.
 */
public final class DuplicateDocumentException extends RuntimeException {

    public static final String PROBLEM_TYPE = "urn:problem-type:duplicate-document";

    private final UUID existingDocumentId;

    public DuplicateDocumentException(UUID existingDocumentId) {
        super("A document with the same checksum already exists: " + existingDocumentId);
        this.existingDocumentId = existingDocumentId;
    }

    public UUID existingDocumentId() {
        return existingDocumentId;
    }
}
