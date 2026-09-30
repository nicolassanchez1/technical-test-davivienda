package io.github.nicolassanchez1.technicaltestdavivienda.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentCategory;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.github.nicolassanchez1.technicaltestdavivienda.support.AbstractIntegrationTest;
import io.github.nicolassanchez1.technicaltestdavivienda.support.SearchCorpus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

/**
 * The search endpoint against a real PostgreSQL, because full-text search is the feature: an
 * accent-folding analyzer, phrase positions, weights and ranking cannot be mocked without the test
 * asserting the mock instead of the behaviour.
 */
class SearchIT extends AbstractIntegrationTest {

    private static final String START = "⟦";
    private static final String STOP = "⟧";

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    private RestClient client;
    private SearchCorpus corpus;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        corpus = new SearchCorpus(jdbcClient);
        corpus.clear();
        givenTheCorpus();
    }

    /**
     * Four Spanish documents that overlap on purpose: the same word appears in a title here, in a
     * tag there and only in the body somewhere else, which is what lets the weighting be asserted.
     */
    private void givenTheCorpus() {
        corpus.indexed(
                "Guía de despliegue continuo",
                "Equipo de plataforma",
                DocumentCategory.ARCHITECTURE_GUIDE,
                List.of("kubernetes", "despliegue"),
                "El despliegue azul verde reduce el tiempo de inactividad durante la publicación del inventario.",
                "La carga de trabajo se reparte sin balanceo dedicado en el clúster de pruebas.");

        corpus.indexed(
                "Manual de operación del clúster",
                "Área de arquitectura",
                DocumentCategory.MANUAL,
                List.of("operacion"),
                "La configuración de red y el balanceo de carga del inventario se describen a continuación.",
                "Los registros de auditoría se conservan durante noventa días.",
                "Las consultas de estado del clúster se responden desde la memoria.");

        corpus.indexed(
                "Especificación del motor de búsqueda",
                "Equipo de plataforma",
                DocumentCategory.SPECIFICATION,
                List.of("busqueda", "postgresql"),
                "El índice invertido resuelve las consultas del inventario sin recorrer la tabla completa.",
                "Las consultas se ordenan por relevancia antes de paginar.");

        corpus.indexed(
                "Auditoría de accesos",
                "Equipo de plataforma",
                DocumentCategory.OTHER,
                List.of("seguridad"),
                "Cada intento de ingreso al inventario queda registrado con su origen y su resultado.");

        corpus.analyze();
    }

    // ---------------------------------------------------------------- matching

    /** The corpus is large and the database is shared, so it never outlives the test. */
    @AfterEach
    void tearDown() {
        corpus.clear();
    }

    @Test
    void findsAccentedContentFromAnAccentFreeQuery() {
        assertThat(titlesOf(search("publicacion"))).containsExactly("Guía de despliegue continuo");
        assertThat(titlesOf(search("operacion"))).contains("Manual de operación del clúster");
        assertThat(titlesOf(search("auditoria"))).contains("Auditoría de accesos");
    }

    @Test
    void matchesAQuotedPhraseOnlyWhereTheWordsAreAdjacent() {
        assertThat(titlesOf(search("balanceo carga"))).hasSize(2);

        assertThat(titlesOf(search("\"balanceo de carga\""))).containsExactly("Manual de operación del clúster");
    }

    @Test
    void excludesTheDocumentsCarryingAMinusTerm() {
        assertThat(titlesOf(search("consultas")))
                .containsExactlyInAnyOrder("Manual de operación del clúster", "Especificación del motor de búsqueda");

        assertThat(titlesOf(search("consultas -especificacion"))).containsExactly("Manual de operación del clúster");
    }

    @Test
    void findsAWordThatOnlyEverAppearsInATitle() {
        assertThat(titlesOf(search("motor"))).containsExactly("Especificación del motor de búsqueda");
    }

    @Test
    void findsAWordThatOnlyEverAppearsInATag() {
        assertThat(titlesOf(search("postgresql"))).containsExactly("Especificación del motor de búsqueda");
        assertThat(titlesOf(search("kubernetes"))).containsExactly("Guía de despliegue continuo");
    }

    @Test
    void findsADocumentByItsAuthorAsASearchTerm() {
        // The criterion names metadata as searchable text, not only as a filter. "Área" also proves
        // the author is indexed through the accent-insensitive configuration.
        assertThat(titlesOf(search("area de arquitectura"))).containsExactly("Manual de operación del clúster");
    }

    @Test
    void findsADocumentByItsCategoryAsASearchTerm() {
        assertThat(titlesOf(search("architecture_guide"))).containsExactly("Guía de despliegue continuo");
    }

    @Test
    void answersANegationThatMatchesEveryDocumentButTheExcludedOne() {
        // The most expensive shape the endpoint accepts: it matches every indexed chunk, so it is
        // the one that would reach the statement timeout first as the corpus grows.
        assertThat(titlesOf(search("inventario -auditoria")))
                .doesNotContain("Auditoría de accesos")
                .hasSize(3);
    }

    @Test
    void ranksATitleMatchAboveABodyMatchOfTheSameWord() {
        List<String> titles = titlesOf(search("auditoria"));

        assertThat(titles).containsExactly("Auditoría de accesos", "Manual de operación del clúster");
    }

    @Test
    void returnsOneRowPerDocumentEvenWhenSeveralOfItsChunksMatch() {
        JsonNode results = search("consultas");

        assertThat(titlesOf(results))
                .containsExactlyInAnyOrder("Manual de operación del clúster", "Especificación del motor de búsqueda");
        assertThat(results.get("total").asLong()).isEqualTo(2);
    }

    @Test
    void neverReturnsADocumentThatIsStillProcessingOrThatFailed() {
        corpus.inStatus(DocumentStatus.PROCESSING, "Borrador en proceso", "Aún falta indexar el inventario.");
        corpus.inStatus(DocumentStatus.FAILED, "Borrador roto", "El inventario no pudo leerse.");

        JsonNode results = search("inventario");

        assertThat(titlesOf(results))
                .doesNotContain("Borrador en proceso", "Borrador roto")
                .hasSize(4);
        assertThat(results.get("total").asLong()).isEqualTo(4);
    }

    // ---------------------------------------------------------------- highlighting

    @Test
    void wrapsTheMatchedWordInTheSnippetWithTheSentinels() {
        JsonNode hit = search("publicacion").get("items").get(0);

        assertThat(hit.get("snippet").asText())
                .contains(START + "publicación" + STOP)
                .doesNotContain("<mark>")
                .doesNotContain("<b>");
    }

    @Test
    void wrapsTheMatchedWordInTheTitleWithTheSameSentinels() {
        JsonNode hit = search("motor").get("items").get(0);

        assertThat(hit.get("titleHighlight").asText())
                .isEqualTo("Especificación del " + START + "motor" + STOP + " de búsqueda");
        assertThat(hit.get("title").asText()).isEqualTo("Especificación del motor de búsqueda");
    }

    @Test
    void leavesTheTitleAloneWhenOnlyTheBodyMatched() {
        JsonNode hit = search("publicacion").get("items").get(0);

        assertThat(hit.get("titleHighlight").asText()).isEqualTo("Guía de despliegue continuo");
    }

    @Test
    void reportsWhereTheFragmentCameFromSoTheViewerCanScrollToIt() {
        JsonNode hit = search("auditoria").get("items").get(1);

        assertThat(hit.get("chunkIndex").asInt()).isEqualTo(1);
        assertThat(hit.get("page").asInt()).isEqualTo(2);
        assertThat(hit.get("heading").asText()).isEqualTo("Seccion 1");
    }

    // ---------------------------------------------------------------- filters

    @Test
    void narrowsByCategory() {
        assertThat(titlesOf(search("inventario"))).hasSize(4);

        assertThat(titlesOf(search("inventario&category=MANUAL"))).containsExactly("Manual de operación del clúster");
    }

    @Test
    void narrowsByAuthor() {
        assertThat(titlesOf(search("inventario&author=" + encoded("Área de arquitectura"))))
                .containsExactly("Manual de operación del clúster");
    }

    @Test
    void narrowsByTagsRequiringEveryTagGiven() {
        assertThat(titlesOf(search("inventario&tags=postgresql")))
                .containsExactly("Especificación del motor de búsqueda");
        assertThat(titlesOf(search("inventario&tags=busqueda,postgresql")))
                .containsExactly("Especificación del motor de búsqueda");
        assertThat(titlesOf(search("inventario&tags=busqueda,kubernetes"))).isEmpty();
    }

    @Test
    void combinesTheFilters() {
        String author = encoded("Equipo de plataforma");

        assertThat(titlesOf(search("inventario&author=" + author))).hasSize(3);
        assertThat(titlesOf(search("inventario&author=" + author + "&category=SPECIFICATION")))
                .containsExactly("Especificación del motor de búsqueda");
        assertThat(titlesOf(search("inventario&author=" + author + "&category=MANUAL")))
                .isEmpty();
    }

    @Test
    void countsDocumentsRatherThanChunksWhenFiltering() {
        JsonNode results = search("inventario&category=SPECIFICATION");

        assertThat(results.get("total").asLong()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- paging

    @Test
    void walksThePagesWithoutRepeatingOrLosingADocument() {
        JsonNode first = search("inventario&page=1&pageSize=2");
        JsonNode second = search("inventario&page=2&pageSize=2");

        assertThat(first.get("total").asLong()).isEqualTo(4);
        assertThat(second.get("total").asLong()).isEqualTo(4);
        assertThat(first.get("items")).hasSize(2);
        assertThat(second.get("items")).hasSize(2);

        List<String> walked = new ArrayList<>(titlesOf(first));
        walked.addAll(titlesOf(second));
        assertThat(walked).doesNotHaveDuplicates().hasSize(4);
    }

    @Test
    void stillReportsHowManyMatchedOnAPagePastTheLastOne() {
        JsonNode beyond = search("inventario&page=3&pageSize=2");

        // The interface renders "N resultados" from this number, so it has to survive an empty page.
        assertThat(beyond.get("items")).isEmpty();
        assertThat(beyond.get("total").asLong()).isEqualTo(4);
    }

    @Test
    void keepsTheSameOrderWhenThePageIsAskedForTwice() {
        assertThat(titlesOf(search("inventario&page=1&pageSize=3")))
                .isEqualTo(titlesOf(search("inventario&page=1&pageSize=3")));
    }

    @Test
    void capsThePageSizeAtTheConfiguredCeiling() {
        JsonNode results = search("inventario&pageSize=5000");

        assertThat(results.get("pageSize").asInt()).isEqualTo(50);
    }

    @Test
    void reportsTheTotalAsTheNumberOfDocumentsNotOfChunks() {
        JsonNode results = search("consultas&pageSize=1");

        assertThat(results.get("items")).hasSize(1);
        assertThat(results.get("total").asLong()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- latency and failure

    @Test
    void reportsHowLongTheSearchTookInTheBodyAndInTheHeader() {
        ResponseEntity<String> response = exchange("/api/search?q=inventario");

        JsonNode results = parse(response.getBody());
        assertThat(results.get("tookMs").asLong()).isGreaterThanOrEqualTo(0).isLessThan(1_000);
        assertThat(response.getHeaders().getFirst("Server-Timing"))
                .isEqualTo("search;dur=" + results.get("tookMs").asLong());
    }

    @Test
    void refusesAnEmptyQueryWithAnAnswerAReaderCanActOn() {
        ResponseEntity<String> response = exchange("/api/search?q=");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");
        assertThat(parse(response.getBody()).get("detail").asText()).contains("at least one searchable word");
        assertThat(parse(response.getBody()).get("type").asText()).isEqualTo("urn:problem-type:invalid-search-query");
    }

    @Test
    void refusesAQueryThatTheAnalyzerReducesToNothing() {
        assertThat(exchange("/api/search?q=" + encoded("de la y el"))
                        .getStatusCode()
                        .value())
                .isEqualTo(400);
        assertThat(exchange("/api/search?q=" + encoded("!!! ... ???"))
                        .getStatusCode()
                        .value())
                .isEqualTo(400);
    }

    @Test
    void refusesAPageBelowTheFirstAndOneBeyondTheDeepestServed() {
        assertThat(exchange("/api/search?q=inventario&page=0").getStatusCode().value())
                .isEqualTo(400);
        assertThat(exchange("/api/search?q=inventario&page=300000000&pageSize=10")
                        .getStatusCode()
                        .value())
                .isEqualTo(400);
    }

    @Test
    void refusesACategoryOutsideTheContract() {
        assertThat(exchange("/api/search?q=inventario&category=INVENTADA")
                        .getStatusCode()
                        .value())
                .isEqualTo(400);
    }

    @Test
    void answersAnEmptyPageWhenNothingMatches() {
        JsonNode results = search("termometro");

        assertThat(results.get("items")).isEmpty();
        assertThat(results.get("total").asLong()).isZero();
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode search(String queryString) {
        return parse(exchange("/api/search?q=" + encoded(queryString)).getBody());
    }

    /** Only the query itself is encoded; anything after an ampersand is already a parameter. */
    private static String encoded(String queryString) {
        int firstParameter = queryString.indexOf('&');
        if (firstParameter < 0) {
            return URLEncoder.encode(queryString, StandardCharsets.UTF_8);
        }
        return URLEncoder.encode(queryString.substring(0, firstParameter), StandardCharsets.UTF_8)
                + queryString.substring(firstParameter);
    }

    private static List<String> titlesOf(JsonNode results) {
        List<String> titles = new ArrayList<>();
        results.get("items").forEach(item -> titles.add(item.get("title").asText()));
        return titles;
    }

    /**
     * The path is already percent encoded, so it is handed over as a {@link URI}: given a string,
     * the client would treat it as a template and encode the escapes a second time, which would
     * quietly turn a quoted phrase into a search for the escape characters themselves.
     */
    private ResponseEntity<String> exchange(String path) {
        URI uri = URI.create("http://localhost:" + port + path);
        return client.get().uri(uri).exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private static JsonNode parse(String body) {
        try {
            return JSON.readTree(body);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
