package io.github.nicolassanchez1.technicaltestdavivienda.search.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentCategory;
import io.github.nicolassanchez1.technicaltestdavivienda.search.application.SearchCriteria;
import io.github.nicolassanchez1.technicaltestdavivienda.search.application.SearchFilters;
import io.github.nicolassanchez1.technicaltestdavivienda.search.application.SearchResults;
import io.github.nicolassanchez1.technicaltestdavivienda.support.AbstractIntegrationTest;
import io.github.nicolassanchez1.technicaltestdavivienda.support.SearchCorpus;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The access path itself. A search that reads the table instead of the index still returns the
 * right answers, so only the plan can tell the two apart, and only a corpus large enough for the
 * planner to have a choice makes the answer mean anything.
 */
class PostgresSearchEngineIT extends AbstractIntegrationTest {

    private static final String GIN_INDEX = "document_chunks_search_vector_idx";

    /** Three hundred documents of ten chunks: enough pages that reading them all is the worse plan. */
    private static final int FILLER_DOCUMENTS = 300;

    private static final int FILLER_CHUNKS_PER_DOCUMENT = 10;

    @Autowired
    private PostgresSearchEngine engine;

    @Autowired
    private JdbcClient jdbcClient;

    private SearchCorpus corpus;

    @BeforeEach
    void setUp() {
        corpus = new SearchCorpus(jdbcClient);
        corpus.clear();
        corpus.fill(FILLER_DOCUMENTS, FILLER_CHUNKS_PER_DOCUMENT);
        corpus.indexed(
                "Guía de despliegue continuo",
                "Equipo de plataforma",
                DocumentCategory.ARCHITECTURE_GUIDE,
                List.of("kubernetes"),
                "El despliegue azul verde reduce el tiempo de inactividad durante la publicación.");
        corpus.indexed(
                "Especificación del motor de búsqueda",
                "Equipo de plataforma",
                DocumentCategory.SPECIFICATION,
                List.of("postgresql"),
                "El índice invertido resuelve el despliegue de consultas sin recorrer la tabla.");
        corpus.analyze();
    }

    private static SearchCriteria criteria(SearchFilters filters) {
        return new SearchCriteria("despliegue", filters, 10, 0);
    }

    /** The corpus is large and the database is shared, so it never outlives the test. */
    @AfterEach
    void tearDown() {
        corpus.clear();
    }

    @Test
    void reachesTheChunksThroughTheGinIndexRatherThanByReadingTheTable() {
        String plan = engine.explainSearch(criteria(SearchFilters.none()));

        assertThat(plan).contains("Bitmap Index Scan on " + GIN_INDEX);
        assertThat(plan).doesNotContain("Seq Scan on document_chunks");
    }

    /**
     * Filters this narrow leave a single candidate document, and the planner then reads that
     * document through its own index and checks the vector on the one chunk it owns. That is a
     * cheaper path than the posting lists, and it is still an indexed one: what must never appear,
     * with or without filters, is a table read end to end.
     */
    @Test
    void neverFallsBackToReadingATableWhenEveryMetadataFilterIsApplied() {
        String plan = engine.explainSearch(criteria(everyFilter()));

        assertThat(plan).doesNotContain("Seq Scan");
        assertThat(plan).contains("Index Scan using documents_category_idx");
    }

    @Test
    void filtersTheMetadataInsideTheRankedStepRatherThanAfterIt() {
        String plan = engine.explainSearch(criteria(everyFilter()));

        assertThat(plan)
                .contains("tags @> ")
                .contains("category = 'SPECIFICATION'")
                .contains("author = 'Equipo de plataforma'");

        // Everything below the window that counts the matches belongs to the ranked step.
        assertThat(plan.indexOf("category = 'SPECIFICATION'")).isGreaterThan(plan.indexOf("WindowAgg"));
        assertThat(plan.indexOf("tags @> ")).isGreaterThan(plan.indexOf("WindowAgg"));
    }

    private static SearchFilters everyFilter() {
        return new SearchFilters(DocumentCategory.SPECIFICATION, "Equipo de plataforma", List.of("postgresql"));
    }

    @Test
    void stopsAtTheRequestedPageSoTheHighlightNeverRunsOverEveryMatch() {
        String plan = engine.explainSearch(new SearchCriteria("despliegue", SearchFilters.none(), 1, 0));

        assertThat(plan).contains("Limit");
    }

    @Test
    void answersWhetherAQueryCarriesAnythingSearchable() {
        assertThat(engine.hasSearchableTerms("despliegue")).isTrue();
        assertThat(engine.hasSearchableTerms("\"balanceo de carga\"")).isTrue();
        assertThat(engine.hasSearchableTerms("")).isFalse();
        assertThat(engine.hasSearchableTerms("de la y el")).isFalse();
        assertThat(engine.hasSearchableTerms("!!! ... ???")).isFalse();
    }

    @Test
    void writesOnlyTheRequestedFiltersIntoTheStatement() {
        String unfiltered = PostgresSearchEngine.searchStatement(SearchFilters.none());
        String byCategory =
                PostgresSearchEngine.searchStatement(new SearchFilters(DocumentCategory.MANUAL, null, List.of()));

        assertThat(unfiltered)
                .doesNotContain(":category")
                .doesNotContain(":author")
                .doesNotContain(":tags");
        assertThat(byCategory).contains("d.category = :category").doesNotContain(":author");
    }

    @Test
    void findsTheDocumentsThatMatchAmongAMuchLargerCorpus() {
        SearchResults results = engine.search(criteria(SearchFilters.none()));

        assertThat(results.total()).isEqualTo(2);
        assertThat(results.hits())
                .extracting(hit -> hit.title())
                .containsExactlyInAnyOrder("Guía de despliegue continuo", "Especificación del motor de búsqueda");
    }
}
