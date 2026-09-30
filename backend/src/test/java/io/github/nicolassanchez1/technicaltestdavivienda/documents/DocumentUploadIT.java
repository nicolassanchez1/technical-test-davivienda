package io.github.nicolassanchez1.technicaltestdavivienda.documents;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure.DocumentsMessagingConfiguration;
import io.github.nicolassanchez1.technicaltestdavivienda.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Exercises HU-01 against a real servlet container, a real PostgreSQL and a real RabbitMQ. The
 * ordering guarantee this covers cannot be observed any other way: the job must exist on the queue
 * only once the row it names is committed.
 */
class DocumentUploadIT extends AbstractIntegrationTest {

    private static final long RECEIVE_TIMEOUT_MS = 5_000;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        jdbcClient.sql("DELETE FROM documents").update();
        drainQueue();
    }

    private void drainQueue() {
        while (rabbitTemplate.receive(DocumentsMessagingConfiguration.PROCESS_QUEUE, 50) != null) {
            // Left over from a previous test in the shared context.
        }
    }

    private static MultiValueMap<String, Object> multipart(String metadataJson, String... filenameThenBody) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (int index = 0; index < filenameThenBody.length; index += 2) {
            String filename = filenameThenBody[index];
            byte[] content = filenameThenBody[index + 1].getBytes(StandardCharsets.UTF_8);
            body.add("files", new NamedResource(filename, content));
        }
        body.add("metadata", metadataJson);
        return body;
    }

    /** A byte array that carries a filename, which is what makes it a file part rather than a field. */
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

    private static String metadata(String... titles) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < titles.length; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(
                    """
                    {"title":"%s","author":"Equipo","category":"MANUAL","tags":["infra"],"version":"1.0"}"""
                            .formatted(titles[index]));
        }
        return json.append(']').toString();
    }

    private ResponseEntity<String> post(String metadataJson, String... filenameThenBody) {
        return client.post()
                .uri("/api/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart(metadataJson, filenameThenBody))
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                        .headers(response.getHeaders())
                        .body(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    @Test
    void acceptsASingleUploadImmediatelyAndPointsAtTheTrackingResource() {
        ResponseEntity<String> response = post(metadata("Guia"), "guia.md", "# Guia\n\nContenido tecnico.");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(202));
        assertThat(response.getBody()).contains("\"status\":\"PROCESANDO\"").contains("guia.md");
        assertThat(response.getHeaders().getFirst("Location")).startsWith("/api/documents/");
    }

    @Test
    void recordsTheUploadAsProcessing() {
        post(metadata("Guia"), "guia.md", "# Guia\n\nContenido.");

        String status = jdbcClient
                .sql("SELECT status FROM documents WHERE original_filename = 'guia.md'")
                .query(String.class)
                .single();

        assertThat(status).isEqualTo("PROCESANDO");
    }

    @Test
    void publishesTheProcessingJobOnlyOnceTheRowIsCommitted() {
        post(metadata("Guia"), "guia.md", "# Guia\n\nContenido para indexar.");

        Message job = rabbitTemplate.receive(DocumentsMessagingConfiguration.PROCESS_QUEUE, RECEIVE_TIMEOUT_MS);

        assertThat(job).isNotNull();
        String documentId = job.getMessageProperties().getMessageId();
        Integer committedRows = jdbcClient
                .sql("SELECT count(*) FROM documents WHERE id = CAST(:id AS uuid)")
                .param("id", documentId)
                .query(Integer.class)
                .single();
        assertThat(committedRows).isEqualTo(1);
    }

    @Test
    void acceptsABatchAndPublishesOneJobPerDocument() {
        ResponseEntity<String> response =
                post(metadata("Uno", "Dos"), "uno.md", "# Uno\n\nTexto.", "dos.txt", "Texto plano.");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(202));
        assertThat(response.getHeaders().getFirst("Location")).isNull();
        assertThat(rabbitTemplate.receive(DocumentsMessagingConfiguration.PROCESS_QUEUE, RECEIVE_TIMEOUT_MS))
                .isNotNull();
        assertThat(rabbitTemplate.receive(DocumentsMessagingConfiguration.PROCESS_QUEUE, RECEIVE_TIMEOUT_MS))
                .isNotNull();
    }

    @Test
    void rejectsTheWholeBatchWhenOneFileIsNotAcceptedAndStoresNothing() {
        ResponseEntity<String> response =
                post(metadata("Bueno", "Malo"), "bueno.md", "# Bueno\n\nTexto.", "malo.exe", "binario");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(422));
        assertThat(response.getHeaders().getContentType())
                .satisfies(type -> assertThat(type.toString()).startsWith("application/problem+json"));
        assertThat(response.getBody()).contains("UNSUPPORTED_FORMAT").contains("EXTENSION_ALLOWLIST");

        Integer rows = jdbcClient
                .sql("SELECT count(*) FROM documents")
                .query(Integer.class)
                .single();
        assertThat(rows).isZero();
        assertThat(rabbitTemplate.receive(DocumentsMessagingConfiguration.PROCESS_QUEUE, 200))
                .isNull();
    }

    @Test
    void pointsADuplicateUploadAtTheDocumentThatAlreadyHoldsIt() {
        post(metadata("Guia"), "guia.md", "# Guia\n\nMismo contenido.");

        ResponseEntity<String> duplicate = post(metadata("Copia"), "copia.md", "# Guia\n\nMismo contenido.");

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(422));
        assertThat(duplicate.getBody()).contains("UNIQUE_CHECKSUM").contains("existingDocumentId");
    }

    @Test
    void refusesAnUploadWhoseMetadataIsNotValid() {
        ResponseEntity<String> response = post(
                """
                [{"title":"","author":"Equipo","category":"MANUAL","tags":[],"version":"1.0"}]""",
                "guia.md",
                "# Guia\n\nTexto.");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(422));
    }
}
