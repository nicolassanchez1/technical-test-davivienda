package io.github.nicolassanchez1.technicaltestdavivienda.documents.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A stored document and everything the pipeline knows about it. The components mirror the
 * {@code documents} table one for one, so the JDBC adapter stays a plain mapping with no hidden
 * translation a reviewer would have to reconstruct.
 */
public record Document(
        UUID id,
        String title,
        String author,
        DocumentCategory category,
        List<String> tags,
        String version,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        String storageKey,
        String sha256,
        DocumentStatus status,
        DocumentErrorCode errorCode,
        String errorMessage,
        Integer pageCount,
        Integer chunkCount,
        Long processingMs,
        Instant createdAt,
        Instant updatedAt,
        Instant indexedAt) {

    public Document {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
