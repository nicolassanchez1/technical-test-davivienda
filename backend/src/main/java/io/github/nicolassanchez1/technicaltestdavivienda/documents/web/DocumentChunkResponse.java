package io.github.nicolassanchez1.technicaltestdavivienda.documents.web;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentChunk;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One slice of a document's body")
public record DocumentChunkResponse(int chunkIndex, Integer page, String heading, String content) {

    public static DocumentChunkResponse of(DocumentChunk chunk) {
        return new DocumentChunkResponse(chunk.chunkIndex(), chunk.page(), chunk.heading(), chunk.content());
    }
}
