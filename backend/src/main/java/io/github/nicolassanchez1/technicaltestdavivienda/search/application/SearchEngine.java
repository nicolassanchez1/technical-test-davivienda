package io.github.nicolassanchez1.technicaltestdavivienda.search.application;

/**
 * The outbound port the search use case talks to.
 *
 * <p>Keeping it a port is what lets PostgreSQL full-text search be replaced by OpenSearch without
 * the use case changing: both can say whether a raw query carries anything searchable at all, and
 * both can return a ranked, highlighted page.
 */
public interface SearchEngine {

    /**
     * Whether the engine can turn this raw query into something to look for. A query made only of
     * punctuation or of words too common to index carries no term, and asking the engine is what
     * keeps that judgement with the analyzer instead of a word list guessed at in Java.
     */
    boolean hasSearchableTerms(String query);

    SearchResults search(SearchCriteria criteria);
}
