package io.github.nicolassanchez1.technicaltestdavivienda.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentRepository;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.FileStorage;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.JobPublisher;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure.DocumentsMessagingConfiguration;
import io.github.nicolassanchez1.technicaltestdavivienda.support.AbstractWorkerIntegrationTest;
import io.github.nicolassanchez1.technicaltestdavivienda.support.DocumentFixtures;
import io.github.nicolassanchez1.technicaltestdavivienda.support.PdfFixtures;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The worker end to end: a job on {@code documents.process} is consumed, the document is indexed,
 * and a file that can never yield text is written off and dead-lettered instead of coming back.
 */
class DocumentProcessingWorkerIT extends AbstractWorkerIntegrationTest {

    private static final Duration PROCESSED_WITHIN = Duration.ofSeconds(20);
    private static final long RECEIVE_TIMEOUT_MS = 10_000;

    /**
     * The api's own subscription queue is declared under {@code @Profile("!worker")}, so in this
     * process nothing is bound to the fan-out and an event would be discarded unobserved. The spy is
     * durable because the template attaches and drops a consumer, which would delete an auto-delete
     * queue, and RabbitMQ 4 refuses a transient non-exclusive one.
     */
    private static final String SUBSCRIBER_QUEUE = "documents.status.worker-test-subscriber";

    private static final String MARKDOWN =
            """
            # Especificación técnica del buscador

            La aplicación indexa documentos técnicos y expone una búsqueda por palabras clave.
            """;

    @TempDir
    private Path scratch;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private FileStorage storage;

    @Autowired
    private JobPublisher jobs;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @BeforeEach
    void setUp() {
        amqpAdmin.declareQueue(new Queue(SUBSCRIBER_QUEUE, true, false, false));
        amqpAdmin.declareBinding(BindingBuilder.bind(new Queue(SUBSCRIBER_QUEUE))
                .to(new FanoutExchange(DocumentsMessagingConfiguration.STATUS_EXCHANGE, true, false)));
        jdbcClient.sql("DELETE FROM documents").update();
        drain(DocumentsMessagingConfiguration.DEAD_LETTER_QUEUE);
        drain(SUBSCRIBER_QUEUE);
    }

    private void drain(String queue) {
        while (rabbitTemplate.receive(queue, 50) != null) {
            // Left over from a previous test in the shared context.
        }
    }

    private Document given(String title, String extension, InputStream content) {
        String storageKey = storage.store(content, extension);
        return documents.save(DocumentFixtures.processing(title, storageKey));
    }

    private Document reload(UUID documentId) {
        return documents.findById(documentId).orElseThrow();
    }

    @Test
    void indexesADocumentTheQueueHandsIt() {
        Document document =
                given("Guía Davivienda", "md", new ByteArrayInputStream(MARKDOWN.getBytes(StandardCharsets.UTF_8)));

        jobs.publishProcessingJob(document.id());

        await().atMost(PROCESSED_WITHIN)
                .untilAsserted(() -> assertThat(reload(document.id()).status()).isEqualTo(DocumentStatus.INDEXED));
        Integer chunks = jdbcClient
                .sql("SELECT count(*) FROM document_chunks WHERE document_id = :id")
                .param("id", document.id())
                .query(Integer.class)
                .single();
        assertThat(chunks).isEqualTo(1);
    }

    @Test
    void announcesTheFailureSoTheInterfaceCanStopWaiting() {
        Path scanned = PdfFixtures.write(scratch.resolve("sin-texto.pdf"), PdfFixtures.Page.imageOnly());
        Document document = given("Plano sin texto", "pdf", read(scanned));

        jobs.publishProcessingJob(document.id());

        Message event = rabbitTemplate.receive(SUBSCRIBER_QUEUE, RECEIVE_TIMEOUT_MS);

        assertThat(event).isNotNull();
        String body = new String(event.getBody(), StandardCharsets.UTF_8);
        // A reader waiting on this document has to learn both that it stopped and why.
        assertThat(body)
                .contains(document.id().toString())
                .contains(DocumentStatus.FAILED.wireValue())
                .contains(DocumentErrorCode.PDF_NO_TEXT_LAYER.name());
    }

    @Test
    void writesOffAScannedPdfAndDeadLettersItsJob() {
        Path scanned = PdfFixtures.write(scratch.resolve("escaneado.pdf"), PdfFixtures.Page.imageOnly());
        Document document = given("Plano escaneado", "pdf", read(scanned));

        jobs.publishProcessingJob(document.id());

        await().atMost(PROCESSED_WITHIN).untilAsserted(() -> {
            Document failed = reload(document.id());
            assertThat(failed.status()).isEqualTo(DocumentStatus.FAILED);
            assertThat(failed.errorCode()).isEqualTo(DocumentErrorCode.PDF_NO_TEXT_LAYER);
            assertThat(failed.errorMessage()).isNotBlank();
        });

        // The job must not be redelivered: the same bytes would fail the same way for ever.
        Message deadLettered =
                rabbitTemplate.receive(DocumentsMessagingConfiguration.DEAD_LETTER_QUEUE, RECEIVE_TIMEOUT_MS);
        assertThat(deadLettered).isNotNull();
        assertThat(deadLettered.getMessageProperties().getMessageId())
                .isEqualTo(document.id().toString());
        assertThat(rabbitTemplate.receive(DocumentsMessagingConfiguration.PROCESS_QUEUE, 200))
                .isNull();
    }

    @Test
    void retriesAFailureThatMayStillGoAwayBeforeGivingUpOnTheDocument() {
        Document document = given(
                "Informe trimestral", "txt", new ByteArrayInputStream("contenido".getBytes(StandardCharsets.UTF_8)));
        // What a worker sees when the shared volume has not published the file yet. Nothing about
        // the document says it can never be read, so the job is worth another attempt.
        storage.delete(document.storageKey());
        Instant startedAt = Instant.now();

        jobs.publishProcessingJob(document.id());

        await().atMost(PROCESSED_WITHIN).untilAsserted(() -> {
            Document failed = reload(document.id());
            assertThat(failed.status()).isEqualTo(DocumentStatus.FAILED);
            assertThat(failed.errorCode()).isEqualTo(DocumentErrorCode.PROCESSING_FAILED);
        });

        // Three attempts one and two seconds apart: giving up any sooner would not have waited.
        assertThat(Duration.between(startedAt, Instant.now())).isGreaterThan(Duration.ofMillis(2_500));
        Message deadLettered =
                rabbitTemplate.receive(DocumentsMessagingConfiguration.DEAD_LETTER_QUEUE, RECEIVE_TIMEOUT_MS);
        assertThat(deadLettered).isNotNull();
        assertThat(deadLettered.getMessageProperties().getMessageId())
                .isEqualTo(document.id().toString());
    }

    private static InputStream read(Path file) {
        try {
            return Files.newInputStream(file);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }
}
