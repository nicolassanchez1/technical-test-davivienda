package io.github.nicolassanchez1.technicaltestdavivienda.documents;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicolassanchez1.technicaltestdavivienda.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Reading documents back: the list, one document, its body and its original bytes. */
class DocumentCatalogIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        jdbcClient.sql("DELETE FROM documents").update();
    }

    private static final class NamedResource extends ByteArrayResource {
        private final String filename;

        private NamedResource(String filename, byte[] content) {
            super(content);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    private void upload(String title, String filename, String body) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("files", new NamedResource(filename, body.getBytes(StandardCharsets.UTF_8)));
        parts.add(
                "metadata",
                """
                [{"title":"%s","author":"Equipo","category":"MANUAL","tags":["infra"],"version":"1.0"}]"""
                        .formatted(title));
        client.post()
                .uri("/api/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .toBodilessEntity();
    }

    private ResponseEntity<String> get(String path) {
        return client.get().uri(path).exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private String storedId() {
        return jdbcClient
                .sql("SELECT CAST(id AS text) FROM documents ORDER BY created_at DESC LIMIT 1")
                .query(String.class)
                .single();
    }

    @Test
    void listsDocumentsAndFiltersThemByTheirContractStatus() {
        upload("Guia", "guia.md", "# Guia\n\nTexto.");

        assertThat(get("/api/documents").getBody()).contains("\"total\":1").contains("Guia");
        assertThat(get("/api/documents?status=PROCESANDO").getBody()).contains("\"total\":1");
        assertThat(get("/api/documents?status=INDEXADO").getBody()).contains("\"total\":0");
    }

    @Test
    void refusesAStatusFilterOutsideTheContract() {
        assertThat(get("/api/documents?status=PROCESSING").getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void servesOneDocumentWithItsMetadata() {
        upload("Guia", "guia.md", "# Guia\n\nTexto.");

        assertThat(get("/api/documents/" + storedId()).getBody())
                .contains("\"title\":\"Guia\"")
                .contains("PROCESANDO")
                .contains("infra");
    }

    @Test
    void servesAnEmptyBodyUntilTheWorkerHasIndexedTheDocument() {
        upload("Guia", "guia.md", "# Guia\n\nTexto.");

        assertThat(get("/api/documents/" + storedId() + "/content").getBody()).contains("\"chunks\":[]");
    }

    @Test
    void servesTheOriginalFileInline() {
        upload("Guia", "guia.md", "# Guia\n\nContenido tecnico.");

        ResponseEntity<String> file = get("/api/documents/" + storedId() + "/file");

        assertThat(file.getStatusCode().value()).isEqualTo(200);
        assertThat(file.getBody()).contains("Contenido tecnico.");
        assertThat(file.getHeaders().getFirst("Content-Disposition"))
                .contains("inline")
                .contains("guia.md");
    }

    /** Chunks normally arrive from the worker; inserting them directly is what lets the reader be tested now. */
    private void insertChunks(String documentId, int count) {
        for (int index = 0; index < count; index++) {
            jdbcClient
                    .sql(
                            """
                            INSERT INTO document_chunks (document_id, chunk_index, page, heading, content, search_vector)
                            VALUES (CAST(:documentId AS uuid), :chunkIndex, :page, :heading, :content,
                                    to_tsvector('es_unaccent', :content))
                            """)
                    .param("documentId", documentId)
                    .param("chunkIndex", index)
                    .param("page", index + 1)
                    .param("heading", "Seccion " + index)
                    .param("content", "Contenido del fragmento " + index)
                    .update();
        }
    }

    @Test
    void walksTheBodyWithACursorAndStopsAtTheEnd() {
        upload("Guia", "guia.md", "# Guia\n\nTexto.");
        String id = storedId();
        insertChunks(id, 5);

        String firstWindow = get("/api/documents/" + id + "/content?limit=2").getBody();
        assertThat(firstWindow)
                .contains("Contenido del fragmento 0")
                .contains("Contenido del fragmento 1")
                .doesNotContain("Contenido del fragmento 2")
                .contains("\"nextChunkIndex\":2");

        String secondWindow = get("/api/documents/" + id + "/content?fromChunkIndex=2&limit=2")
                .getBody();
        assertThat(secondWindow)
                .contains("Contenido del fragmento 2")
                .contains("Contenido del fragmento 3")
                .contains("\"nextChunkIndex\":4");

        String lastWindow = get("/api/documents/" + id + "/content?fromChunkIndex=4&limit=2")
                .getBody();
        assertThat(lastWindow).contains("Contenido del fragmento 4").contains("\"nextChunkIndex\":null");
    }

    @Test
    void reportsNoCursorWhenOneWindowHoldsTheWholeBody() {
        upload("Guia", "guia.md", "# Guia\n\nTexto.");
        String id = storedId();
        insertChunks(id, 3);

        assertThat(get("/api/documents/" + id + "/content?limit=10").getBody())
                .contains("\"nextChunkIndex\":null")
                .contains("Seccion 2");
    }

    @Test
    void servesTheBodyInChunkOrder() {
        upload("Guia", "guia.md", "# Guia\n\nTexto.");
        String id = storedId();
        insertChunks(id, 3);

        String body = get("/api/documents/" + id + "/content").getBody();

        assertThat(body.indexOf("fragmento 0")).isLessThan(body.indexOf("fragmento 1"));
        assertThat(body.indexOf("fragmento 1")).isLessThan(body.indexOf("fragmento 2"));
    }

    @Test
    void refusesAPageDeepEnoughToOverflowTheOffset() {
        assertThat(get("/api/documents?page=300000000&pageSize=10")
                        .getStatusCode()
                        .value())
                .isEqualTo(400);
    }

    @Test
    void refusesAPageBelowTheFirst() {
        assertThat(get("/api/documents?page=0").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void reportsAnUnknownDocumentAsNotFound() {
        String unknown = "/api/documents/00000000-0000-0000-0000-000000000000";

        assertThat(get(unknown).getStatusCode().value()).isEqualTo(404);
        assertThat(get(unknown + "/content").getStatusCode().value()).isEqualTo(404);
        assertThat(get(unknown + "/file").getStatusCode().value()).isEqualTo(404);
    }
}
