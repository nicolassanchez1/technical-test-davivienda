package io.github.nicolassanchez1.technicaltestdavivienda.search.web;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentCategory;
import io.github.nicolassanchez1.technicaltestdavivienda.search.application.SearchDocuments;
import io.github.nicolassanchez1.technicaltestdavivienda.search.application.SearchFilters;
import io.github.nicolassanchez1.technicaltestdavivienda.search.application.SearchOutcome;
import io.github.nicolassanchez1.technicaltestdavivienda.shared.web.ProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Full-text search over titles, metadata and content, ranked, paged and highlighted. */
@RestController
@RequestMapping("/search")
public class SearchController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final String SERVER_TIMING = "Server-Timing";

    private final SearchDocuments searchDocuments;

    public SearchController(SearchDocuments searchDocuments) {
        this.searchDocuments = searchDocuments;
    }

    @Operation(
            summary = "Search indexed documents",
            description = "Quoted phrases, `or` between alternatives and a leading minus to exclude a word are all "
                    + "understood. Only documents that finished indexing are ever returned.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "A page of ranked matches with highlighted fragments"),
        @ApiResponse(
                responseCode = "400",
                description = "The query carries no searchable term, or the paging is out of range",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemResponse.class))),
        @ApiResponse(
                responseCode = "503",
                description = "The search exceeded its time budget and was cancelled",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemResponse.class)))
    })
    @GetMapping
    public ResponseEntity<SearchResponse> search(
            @Parameter(description = "Words or quoted phrases to look for", example = "indice invertido -borrador")
                    @RequestParam(defaultValue = "")
                    String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int pageSize,
            @RequestParam(required = false) DocumentCategory category,
            @RequestParam(required = false) String author,
            @Parameter(description = "Every tag given must be present on the document") @RequestParam(required = false)
                    List<String> tags) {

        SearchOutcome outcome = searchDocuments.search(q, new SearchFilters(category, author, tags), page, pageSize);

        return ResponseEntity.ok()
                .header(SERVER_TIMING, "search;dur=" + outcome.tookMs())
                .body(SearchResponse.of(outcome));
    }
}
