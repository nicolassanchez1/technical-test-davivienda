package io.github.nicolassanchez1.technicaltestdavivienda.documents.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A window over a document's chunks, paged by a cursor over chunkIndex")
public record DocumentContentResponse(
        List<DocumentChunkResponse> chunks,
        @Schema(description = "Cursor for the next window, absent when the document ends here")
                Integer nextChunkIndex) {}
