package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
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
}
