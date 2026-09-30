package io.github.nicolassanchez1.technicaltestdavivienda.search.application;

import io.github.nicolassanchez1.technicaltestdavivienda.shared.config.AppProperties;
import org.springframework.stereotype.Service;

/**
 * Runs one search: it checks the query is worth running, bounds the paging, calls the engine and
 * reports how long the engine took.
 *
 * <p>The bounds are the latency budget expressed as code. A page size the caller chose freely, or
 * an offset deep enough to make the engine sort the whole corpus, are the two ways a single
 * request can cost far more than the ceiling allows, so both are refused here rather than being
 * discovered as a timeout later.
 */
@Service
public class SearchDocuments {

    /** Paging deeper than this is never a real reader, and it costs the engine a full ranked sort. */
    static final long MAX_OFFSET = 10_000;

    /** Longer than any phrase a reader types, and short enough that parsing it stays free. */
    static final int MAX_QUERY_LENGTH = 256;

    private static final String NO_SEARCHABLE_TERM =
            "The query must contain at least one searchable word. Punctuation alone, or only words too "
                    + "common to index, cannot be searched for.";

    private final SearchEngine engine;
    private final int maxPageSize;

    public SearchDocuments(SearchEngine engine, AppProperties properties) {
        this.engine = engine;
        this.maxPageSize = properties.searchMaxPageSize();
    }

    public SearchOutcome search(String query, SearchFilters filters, int page, int pageSize) {
        String trimmed = query == null ? "" : query.trim();
        requireLengthWithinBounds(trimmed);

        int requestedPage = requirePositive(page);
        int size = boundedPageSize(pageSize);
        int offset = offsetOf(requestedPage, size);

        // Timed from here: the term check is itself a round trip, and a number that hides part of
        // what the caller waited for is not the latency the spec asks to be reported.
        long startedAt = System.nanoTime();
        requireSearchableTerms(trimmed);
        SearchResults results = engine.search(new SearchCriteria(trimmed, filters, size, offset));
        long tookMs = (System.nanoTime() - startedAt) / 1_000_000;

        return new SearchOutcome(results.hits(), results.total(), requestedPage, size, tookMs);
    }

    /**
     * The empty query is rejected without asking the engine: it is the one case that needs no
     * analyzer to recognise, and short-circuiting it keeps a missing parameter from costing a round
     * trip. Everything else is decided by the analyzer itself.
     */
    private void requireSearchableTerms(String query) {
        if (query.isEmpty() || !engine.hasSearchableTerms(query)) {
            throw new InvalidSearchQueryException(NO_SEARCHABLE_TERM);
        }
    }

    private static void requireLengthWithinBounds(String query) {
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new InvalidSearchQueryException(
                    "The query is longer than " + MAX_QUERY_LENGTH + " characters. Search for fewer words.");
        }
    }

    private int boundedPageSize(int requested) {
        return Math.clamp(requested, 1, maxPageSize);
    }

    private static int requirePositive(int page) {
        if (page < 1) {
            throw new IllegalArgumentException("page starts at 1");
        }
        return page;
    }

    /**
     * Computed in long arithmetic and capped: {@code (page - 1) * size} overflows an int for a far
     * enough page, and a negative offset makes PostgreSQL raise an error that would surface as a
     * conflict rather than as the bad request it is.
     */
    private static int offsetOf(int page, int size) {
        long offset = (long) (page - 1) * size;
        if (offset > MAX_OFFSET) {
            throw new IllegalArgumentException("page is beyond the deepest page that can be served");
        }
        return (int) offset;
    }
}
