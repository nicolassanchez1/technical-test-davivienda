package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for document rows. It carries no SQL, so use cases can be exercised against an
 * in-memory fake and the adapter can be replaced without touching them.
 */
public interface DocumentRepository {

    /**
     * Inserts the document and returns the stored row.
     *
     * @throws io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DuplicateDocumentException
     *     when another document already holds the same checksum
     */
    Document save(Document document);

    Optional<Document> findById(UUID id);

    Optional<UUID> findIdBySha256(String sha256);

    /** Newest first. A null status returns documents in every status. */
    List<Document> findAll(DocumentStatus statusOrNull, int limit, int offset);

    /** A null status counts documents in every status. */
    long countAll(DocumentStatus statusOrNull);

    /**
     * Records a finished indexing run, but only on a document that is still being processed.
     *
     * <p>The guard is what makes a redelivered job harmless. Two consumers may hold the same job,
     * and the second one must not overwrite the first one's result: it updates no row, learns that
     * it lost, and rolls its own work back.
     *
     * @return true when this call is the one that moved the document to {@code INDEXADO}
     */
    boolean markIndexed(UUID documentId, int chunkCount, Integer pageCount, long processingMs);

    /**
     * Records why a document could not be indexed, under the same guard as {@link #markIndexed}: a
     * document that already reached a terminal state keeps the state it reached.
     *
     * @return true when this call is the one that moved the document to {@code ERROR}
     */
    boolean markFailed(UUID documentId, DocumentErrorCode errorCode, String errorMessage);

    /**
     * Identifiers of the documents still being processed since before {@code stuckSince}, oldest
     * first. Only the identifiers: a reconciling job carries nothing else, and the worker reads the
     * row it points at anyway.
     */
    List<UUID> findStuckInProcessing(Instant stuckSince, int limit);
}
