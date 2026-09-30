package io.github.nicolassanchez1.technicaltestdavivienda.documents.web;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentCategory;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "A document and everything known about it")
public record DocumentResponse(
        UUID id,
        String title,
        String author,
        DocumentCategory category,
        List<String> tags,
        String version,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        DocumentStatus status,
        @Schema(description = "Why indexing failed, absent while the document is healthy") DocumentErrorCode errorCode,
        String errorMessage,
        Integer pageCount,
        Integer chunkCount,
        Long processingMs,
        Instant createdAt,
        Instant updatedAt,
        Instant indexedAt) {

    public static DocumentResponse of(Document document) {
        return new DocumentResponse(
                document.id(),
                document.title(),
                document.author(),
                document.category(),
                document.tags(),
                document.version(),
                document.originalFilename(),
                document.mimeType(),
                document.sizeBytes(),
                document.status(),
                document.errorCode(),
                document.errorMessage(),
                document.pageCount(),
                document.chunkCount(),
                document.processingMs(),
                document.createdAt(),
                document.updatedAt(),
                document.indexedAt());
    }
}
