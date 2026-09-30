package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentChunk;
import java.util.List;
import java.util.UUID;

/**
 * Outbound port for the read side of a document's body. The viewer walks the chunks with a cursor
 * over {@code chunk_index} rather than an offset, so paging deep into a long document stays a
 * bounded index range scan.
 */
public interface DocumentChunkRepository {

    /**
     * Chunks of one document ordered by {@code chunk_index}, starting at {@code fromChunkIndex}
     * inclusive and returning at most {@code limit} of them.
     */
    List<DocumentChunk> findByDocument(UUID documentId, int fromChunkIndex, int limit);
}
