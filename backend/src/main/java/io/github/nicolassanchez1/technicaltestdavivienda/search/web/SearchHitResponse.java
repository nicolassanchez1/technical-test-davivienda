package io.github.nicolassanchez1.technicaltestdavivienda.search.web;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentCategory;
import io.github.nicolassanchez1.technicaltestdavivienda.search.application.SearchHit;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "One matching document with its best fragment")
public record SearchHitResponse(
        UUID id,
        String title,
        @Schema(
                        description = "The title with every matched word wrapped in the U+27E6 and U+27E7 sentinels",
                        example = "Guia de ⟦despliegue⟧")
                String titleHighlight,
        String author,
        DocumentCategory category,
        List<String> tags,
        String version,
        Instant indexedAt,
        @Schema(description = "Index of the chunk the fragment came from, so the viewer can scroll to it")
                int chunkIndex,
        @Schema(description = "Page the fragment came from, for documents that have pages") Integer page,
        String heading,
        @Schema(description = "Matched fragment, with the same sentinels as the title") String snippet,
        double rank) {

    public static SearchHitResponse of(SearchHit hit) {
        return new SearchHitResponse(
                hit.documentId(),
                hit.title(),
                hit.highlightedTitle(),
                hit.author(),
                hit.category(),
                hit.tags(),
                hit.version(),
                hit.indexedAt(),
                hit.chunkIndex(),
                hit.page(),
                hit.heading(),
                hit.snippet(),
                hit.rank());
    }
}
