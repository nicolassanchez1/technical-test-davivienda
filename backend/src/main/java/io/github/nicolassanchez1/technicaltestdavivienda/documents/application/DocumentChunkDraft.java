package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

/**
 * One chunk about to be inserted: everything {@code document_chunks} needs except the identity the
 * database assigns. Being the chunker's output, it is where the invariants the viewer and the search
 * path rely on are checked, before any SQL runs.
 *
 * @param chunkIndex the position in the document, contiguous and starting at 0
 * @param page the 1-based PDF page this chunk came from, or null for a format without pages
 * @param heading the Markdown section this chunk belongs to, or null when it belongs to none
 */
public record DocumentChunkDraft(int chunkIndex, Integer page, String heading, String content) {

    public DocumentChunkDraft {
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("Chunk indexes start at 0, and this one is " + chunkIndex + ".");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("A chunk must carry content, otherwise it indexes nothing.");
        }
    }
}
