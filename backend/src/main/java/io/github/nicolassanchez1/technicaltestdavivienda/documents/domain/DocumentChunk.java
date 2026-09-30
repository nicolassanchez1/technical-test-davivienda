package io.github.nicolassanchez1.technicaltestdavivienda.documents.domain;

import java.util.UUID;

/**
 * One indexable slice of a document: a PDF page, a Markdown section or a bounded run of plain
 * text. The viewer reads chunks in order, and the search engine ranks them one by one, so a long
 * document never forces a single oversized value through either path.
 */
public record DocumentChunk(long id, UUID documentId, int chunkIndex, Integer page, String heading, String content) {}
