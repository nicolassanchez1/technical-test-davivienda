package io.github.nicolassanchez1.technicaltestdavivienda.search.web;

import io.github.nicolassanchez1.technicaltestdavivienda.search.application.SearchOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One page of search results, highest ranked first")
public record SearchResponse(
        List<SearchHitResponse> items,
        @Schema(description = "Documents matching the query and the filters, not chunks") long total,
        int page,
        int pageSize,
        @Schema(description = "How long the engine took, excluding rendering this response") long tookMs) {

    public static SearchResponse of(SearchOutcome outcome) {
        return new SearchResponse(
                outcome.hits().stream().map(SearchHitResponse::of).toList(),
                outcome.total(),
                outcome.page(),
                outcome.pageSize(),
                outcome.tookMs());
    }
}
