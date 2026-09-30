package io.github.nicolassanchez1.technicaltestdavivienda.search.application;

import java.util.List;

/**
 * A finished search: the page of hits, the paging that produced it and how long the engine took.
 *
 * <p>{@code tookMs} measures the engine call alone, so it reports the cost the latency budget is
 * about rather than the cost of rendering a response.
 */
public record SearchOutcome(List<SearchHit> hits, long total, int page, int pageSize, long tookMs) {

    public SearchOutcome {
        hits = List.copyOf(hits);
    }
}
