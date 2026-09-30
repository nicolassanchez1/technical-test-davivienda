package io.github.nicolassanchez1.technicaltestdavivienda.search.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentCategory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One document that matched, together with the single best fragment found inside it.
 *
 * <p>It is a projection rather than a whole {@code Document}: a hit is always an indexed document,
 * so the failure fields carry nothing, and the storage key and checksum have no business leaving
 * the server. A reader who opens a result fetches the full document by its id.
 *
 * <p>{@code highlightedTitle} and {@code snippet} arrive already marked with the sentinels the
 * frontend turns into highlight nodes, so no HTML is ever produced here.
 */
public record SearchHit(
        UUID documentId,
        String title,
        String highlightedTitle,
        String author,
        DocumentCategory category,
        List<String> tags,
        String version,
        Instant indexedAt,
        int chunkIndex,
        Integer page,
        String heading,
        String snippet,
        double rank) {

    public SearchHit {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
