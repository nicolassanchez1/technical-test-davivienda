package io.github.nicolassanchez1.technicaltestdavivienda.search.application;

import java.util.List;

/**
 * What the engine returns: one page of hits and how many documents match overall. The total counts
 * documents, never chunks, because a document that matches in eight places is still one result.
 */
public record SearchResults(List<SearchHit> hits, long total) {

    public SearchResults {
        hits = List.copyOf(hits);
    }

    public static SearchResults empty() {
        return new SearchResults(List.of(), 0);
    }
}
