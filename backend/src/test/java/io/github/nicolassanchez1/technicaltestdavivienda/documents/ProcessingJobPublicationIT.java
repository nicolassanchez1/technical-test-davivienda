package io.github.nicolassanchez1.technicaltestdavivienda.documents;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentProcessingRequested;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentRepository;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentCategory;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure.DocumentsMessagingConfiguration;
import io.github.nicolassanchez1.technicaltestdavivienda.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The ordering guarantee, tested where it actually lives. A job that reaches the queue before its
 * row is committed would let the worker look up a document that does not exist yet, so the
 * publication must wait for the commit and must not happen at all when the transaction rolls back.
 */
class ProcessingJobPublicationIT extends AbstractIntegrationTest {

    private static final long RECEIVE_TIMEOUT_MS = 5_000;
    private static final long SILENCE_TIMEOUT_MS = 300;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM documents").update();
        while (rabbitTemplate.receive(DocumentsMessagingConfiguration.PROCESS_QUEUE, 50) != null) {
            // Drain anything a previous test left in the shared broker.
        }
    }

    private static String sha(char filler) {
        char[] value = new char[64];
        Arrays.fill(value, filler);
        return new String(value);
    }

    private Document processingDocument(String sha256) {
        Instant now = Instant.now();
        return new Document(
                UUID.randomUUID(),
                "Guia",
                "Equipo",
                DocumentCategory.MANUAL,
                List.of("infra"),
                "1.0",
                "guia.md",
                "text/markdown",
                42,
                UUID.randomUUID() + ".md",
                sha256,
                DocumentStatus.PROCESSING,
                null,
                null,
                null,
                null,
                null,
                now,
                now,
                null);
    }

    @Test
    void holdsTheJobUntilTheTransactionCommits() {
        UUID documentId = transactions.execute(status -> {
            Document saved = documents.save(processingDocument(sha('a')));
            events.publishEvent(new DocumentProcessingRequested(saved.id()));

            // Still inside the transaction: the row is not visible to the worker yet.
            assertThat(rabbitTemplate.receive(DocumentsMessagingConfiguration.PROCESS_QUEUE, SILENCE_TIMEOUT_MS))
                    .isNull();
            return saved.id();
        });

        Message job = rabbitTemplate.receive(DocumentsMessagingConfiguration.PROCESS_QUEUE, RECEIVE_TIMEOUT_MS);

        assertThat(job).isNotNull();
        assertThat(job.getMessageProperties().getMessageId()).isEqualTo(documentId.toString());
    }

    @Test
    void publishesNothingWhenTheTransactionRollsBack() {
        transactions.execute(status -> {
            Document saved = documents.save(processingDocument(sha('b')));
            events.publishEvent(new DocumentProcessingRequested(saved.id()));
            status.setRollbackOnly();
            return null;
        });

        assertThat(rabbitTemplate.receive(DocumentsMessagingConfiguration.PROCESS_QUEUE, SILENCE_TIMEOUT_MS))
                .isNull();
        Integer rows = jdbcClient
                .sql("SELECT count(*) FROM documents")
                .query(Integer.class)
                .single();
        assertThat(rows).isZero();
    }
}
