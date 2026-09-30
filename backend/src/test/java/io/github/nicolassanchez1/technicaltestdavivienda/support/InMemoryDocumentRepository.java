package io.github.nicolassanchez1.technicaltestdavivienda.support;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentRepository;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The document rows, in a map. It reproduces the two behaviours the use cases actually depend on:
 * a save assigns the timestamps, and a status change only applies to a document that is still
 * being processed, which is the guard the real UPDATE carries in its WHERE clause.
 */
public final class InMemoryDocumentRepository implements DocumentRepository {

    private final Map<UUID, Document> rows = new LinkedHashMap<>();

    public List<Document> saved() {
        return List.copyOf(rows.values());
    }

    /** Puts a row in place without going through the use cases, for a test that starts from one. */
    public Document given(Document document) {
        rows.put(document.id(), document);
        return document;
    }

    public Document require(UUID id) {
        Document document = rows.get(id);
        if (document == null) {
            throw new IllegalStateException("No document " + id + " was stored");
        }
        return document;
    }

    @Override
    public Document save(Document document) {
        UUID id = document.id() == null ? UUID.randomUUID() : document.id();
        Instant now = Instant.now();
        Document persisted = copyOf(document, id, document.status(), now);
        rows.put(id, persisted);
        return persisted;
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public Optional<UUID> findIdBySha256(String sha256) {
        return rows.values().stream()
                .filter(document -> document.sha256().equals(sha256))
                .map(Document::id)
                .findFirst();
    }

    @Override
    public List<Document> findAll(DocumentStatus statusOrNull, int limit, int offset) {
        return rows.values().stream()
                .filter(document -> statusOrNull == null || document.status() == statusOrNull)
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long countAll(DocumentStatus statusOrNull) {
        return rows.values().stream()
                .filter(document -> statusOrNull == null || document.status() == statusOrNull)
                .count();
    }

    @Override
    public boolean markIndexed(UUID documentId, int chunkCount, Integer pageCount, long processingMs) {
        Document document = processing(documentId);
        if (document == null) {
            return false;
        }
        Instant now = Instant.now();
        rows.put(
                documentId,
                new Document(
                        document.id(),
                        document.title(),
                        document.author(),
                        document.category(),
                        document.tags(),
                        document.version(),
                        document.originalFilename(),
                        document.mimeType(),
                        document.sizeBytes(),
                        document.storageKey(),
                        document.sha256(),
                        DocumentStatus.INDEXED,
                        null,
                        null,
                        pageCount,
                        chunkCount,
                        processingMs,
                        document.createdAt(),
                        now,
                        now));
        return true;
    }

    @Override
    public boolean markFailed(UUID documentId, DocumentErrorCode errorCode, String errorMessage) {
        Document document = processing(documentId);
        if (document == null) {
            return false;
        }
        rows.put(
                documentId,
                new Document(
                        document.id(),
                        document.title(),
                        document.author(),
                        document.category(),
                        document.tags(),
                        document.version(),
                        document.originalFilename(),
                        document.mimeType(),
                        document.sizeBytes(),
                        document.storageKey(),
                        document.sha256(),
                        DocumentStatus.FAILED,
                        errorCode,
                        errorMessage,
                        document.pageCount(),
                        document.chunkCount(),
                        document.processingMs(),
                        document.createdAt(),
                        Instant.now(),
                        null));
        return true;
    }

    @Override
    public List<UUID> findStuckInProcessing(Instant stuckSince, int limit) {
        return rows.values().stream()
                .filter(document -> document.status() == DocumentStatus.PROCESSING)
                .filter(document -> document.updatedAt().isBefore(stuckSince))
                .sorted(Comparator.comparing(Document::updatedAt))
                .limit(limit)
                .map(Document::id)
                .toList();
    }

    private Document processing(UUID documentId) {
        Document document = rows.get(documentId);
        return document != null && document.status() == DocumentStatus.PROCESSING ? document : null;
    }

    private static Document copyOf(Document document, UUID id, DocumentStatus status, Instant now) {
        return new Document(
                id,
                document.title(),
                document.author(),
                document.category(),
                document.tags(),
                document.version(),
                document.originalFilename(),
                document.mimeType(),
                document.sizeBytes(),
                document.storageKey(),
                document.sha256(),
                status,
                document.errorCode(),
                document.errorMessage(),
                document.pageCount(),
                document.chunkCount(),
                document.processingMs(),
                now,
                now,
                document.indexedAt());
    }
}
