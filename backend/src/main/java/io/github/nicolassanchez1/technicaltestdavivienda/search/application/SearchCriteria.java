package io.github.nicolassanchez1.technicaltestdavivienda.search.application;

/** One page of one search: what to look for, how to narrow it and which slice to return. */
public record SearchCriteria(String query, SearchFilters filters, int limit, int offset) {

    public SearchCriteria {
        filters = filters == null ? SearchFilters.none() : filters;
    }
}
