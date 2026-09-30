package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import java.util.List;
import java.util.UUID;

/**
 * Outbound port for the write side of a document's body. It is separate from {@link
 * DocumentChunkRepository} because the two sides have nothing in common: the viewer reads chunks a
 * page at a time, while the worker replaces all of them at once together with the {@code tsvector}
 * each one is indexed under.
 *
 * <p>Both calls are meant to run inside one transaction owned by the caller, so a document is never
 * visible with half of its chunks removed and half of them rewritten.
 */
public interface DocumentChunkWriter {

    /** Removes every chunk of the document, so that re-indexing replaces them instead of adding to them. */
    int deleteByDocument(UUID documentId);

    /**
     * Inserts every draft in one statement, computing the search vector from the document's title
     * and metadata and from each chunk's content.
     */
    void insertAll(Document document, List<DocumentChunkDraft> chunks);
}
