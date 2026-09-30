package io.github.nicolassanchez1.technicaltestdavivienda.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentCategory;
import io.github.nicolassanchez1.technicaltestdavivienda.shared.config.AppProperties;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What the use case decides before and after the engine runs: which queries are worth sending, and
 * which page it is allowed to ask for. The engine is a fake, so every assertion here is about the
 * rules rather than about PostgreSQL.
 */
class SearchDocumentsTest {

    private static final int MAX_PAGE_SIZE = 50;

    private RecordingSearchEngine engine;
    private SearchDocuments searchDocuments;

    @BeforeEach
    void setUp() {
        engine = new RecordingSearchEngine();
        searchDocuments = new SearchDocuments(
                engine, new AppProperties(Path.of("target", "storage"), 20, 10, 900L, MAX_PAGE_SIZE, 4, 10, 15_000L));
    }

    private SearchOutcome search(String query, int page, int pageSize) {
        return searchDocuments.search(query, SearchFilters.none(), page, pageSize);
    }

    @Test
    void refusesABlankQueryWithoutEvenAskingTheEngine() {
        assertThatThrownBy(() -> search("   ", 1, 10))
                .isInstanceOf(InvalidSearchQueryException.class)
                .hasMessageContaining("at least one searchable word");

        assertThat(engine.parsed).isEmpty();
        assertThat(engine.criteria).isNull();
    }

    @Test
    void refusesAQueryTheEngineFindsNoSearchableTermIn() {
        engine.searchable = false;

        assertThatThrownBy(() -> search("de la y el", 1, 10))
                .isInstanceOf(InvalidSearchQueryException.class)
                .hasMessageContaining("at least one searchable word");

        assertThat(engine.parsed).containsExactly("de la y el");
        assertThat(engine.criteria).isNull();
    }

    @Test
    void refusesAQueryLongerThanTheBound() {
        String tooLong = "a".repeat(SearchDocuments.MAX_QUERY_LENGTH + 1);

        assertThatThrownBy(() -> search(tooLong, 1, 10)).isInstanceOf(InvalidSearchQueryException.class);
        assertThat(engine.parsed).isEmpty();
    }

    @Test
    void refusesAPageBelowTheFirst() {
        assertThatThrownBy(() -> search("despliegue", 0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page starts at 1");

        assertThat(engine.criteria).isNull();
    }

    @Test
    void refusesAPageDeepEnoughToCostAFullRankedSort() {
        int firstRefusedPage = (int) (SearchDocuments.MAX_OFFSET / 10) + 2;

        assertThatThrownBy(() -> search("despliegue", firstRefusedPage, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deepest page");

        assertThat(engine.criteria).isNull();
    }

    @Test
    void refusesAPageWhoseOffsetWouldOverflowAnInt() {
        assertThatThrownBy(() -> search("despliegue", 300_000_000, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void servesTheDeepestPageThatStaysWithinTheCap() {
        int lastAllowedPage = (int) (SearchDocuments.MAX_OFFSET / 10) + 1;

        SearchOutcome outcome = search("despliegue", lastAllowedPage, 10);

        assertThat(outcome.page()).isEqualTo(lastAllowedPage);
        assertThat(engine.criteria.offset()).isEqualTo((int) SearchDocuments.MAX_OFFSET);
    }

    @Test
    void capsAPageSizeAboveTheCeiling() {
        SearchOutcome outcome = search("despliegue", 1, MAX_PAGE_SIZE + 500);

        assertThat(outcome.pageSize()).isEqualTo(MAX_PAGE_SIZE);
        assertThat(engine.criteria.limit()).isEqualTo(MAX_PAGE_SIZE);
    }

    @Test
    void liftsAPageSizeBelowOne() {
        SearchOutcome outcome = search("despliegue", 1, 0);

        assertThat(outcome.pageSize()).isEqualTo(1);
        assertThat(engine.criteria.limit()).isEqualTo(1);
    }

    @Test
    void turnsThePageNumberIntoAnOffset() {
        search("despliegue", 4, 20);

        assertThat(engine.criteria.offset()).isEqualTo(60);
        assertThat(engine.criteria.limit()).isEqualTo(20);
    }

    @Test
    void trimsTheQueryBeforeHandingItToTheEngine() {
        search("  despliegue continuo  ", 1, 10);

        assertThat(engine.criteria.query()).isEqualTo("despliegue continuo");
    }

    @Test
    void carriesTheFiltersAndTheTotalStraightThrough() {
        engine.results = new SearchResults(List.of(hit("Guia de despliegue")), 97);
        SearchFilters filters = new SearchFilters(DocumentCategory.MANUAL, "Equipo", List.of("infra"));

        SearchOutcome outcome = searchDocuments.search("despliegue", filters, 2, 10);

        assertThat(outcome.total()).isEqualTo(97);
        assertThat(outcome.hits()).singleElement().extracting(SearchHit::title).isEqualTo("Guia de despliegue");
        assertThat(engine.criteria.filters()).isEqualTo(filters);
    }

    @Test
    void reportsHowLongTheEngineTookWithoutInventingAFloor() {
        SearchOutcome outcome = search("despliegue", 1, 10);

        assertThat(outcome.tookMs()).isGreaterThanOrEqualTo(0).isLessThan(1_000);
    }

    @Test
    void defaultsMissingFiltersToNone() {
        searchDocuments.search("despliegue", null, 1, 10);

        assertThat(engine.criteria.filters()).isEqualTo(SearchFilters.none());
    }

    private static SearchHit hit(String title) {
        return new SearchHit(
                UUID.randomUUID(),
                title,
                title,
                "Equipo",
                DocumentCategory.MANUAL,
                List.of("infra"),
                "1.0",
                Instant.now(),
                0,
                1,
                "Seccion",
                "fragmento",
                0.5);
    }

    /** A fake engine: it records what it was asked and answers whatever the test set up. */
    private static final class RecordingSearchEngine implements SearchEngine {

        private final List<String> parsed = new ArrayList<>();
        private boolean searchable = true;
        private SearchResults results = SearchResults.empty();
        private SearchCriteria criteria;

        @Override
        public boolean hasSearchableTerms(String query) {
            parsed.add(query);
            return searchable;
        }

        @Override
        public SearchResults search(SearchCriteria criteria) {
            this.criteria = criteria;
            return results;
        }
    }
}
