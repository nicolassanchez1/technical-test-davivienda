package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentChunk;
import java.util.List;

/**
 * A run of a document's body for the viewer.
 *
 * @param nextChunkIndex where the next request should start, or null when the end has been
 *     reached. It is a cursor rather than an offset so a long document pages at constant cost.
 */
public record DocumentContent(List<DocumentChunk> chunks, Integer nextChunkIndex) {

    public DocumentContent {
        chunks = List.copyOf(chunks);
    }
}
