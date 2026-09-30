package io.github.nicolassanchez1.technicaltestdavivienda.documents;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentRepository;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ReconcileStuckDocuments;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure.DocumentsMessagingConfiguration;
import io.github.nicolassanchez1.technicaltestdavivienda.support.AbstractIntegrationTest;
import io.github.nicolassanchez1.technicaltestdavivienda.support.DocumentFixtures;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The safety net, against the real table. A job can be lost between the broker and a worker that
 * dies holding it, and the only trace left is a row that says it is being processed and a clock
 * that keeps running.
 */
class ReconcileStuckDocumentsIT extends AbstractIntegrationTest {

    private static final long RECEIVE_TIMEOUT_MS = 5_000;
    private static final long SILENCE_TIMEOUT_MS = 300;

    @Autowired
    private ReconcileStuckDocuments reconcile;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM documents").update();
        while (rabbitTemplate.receive(DocumentsMessagingConfiguration.PROCESS_QUEUE, 50) != null) {
            // Left over from a previous test in the shared context.
        }
    }

    private Document givenProcessingSince(Duration age) {
        return documents.save(DocumentFixtures.processing(
                "Especificación técnica",
                DocumentFixtures.storageKey("md"),
                Instant.now().minus(age)));
    }

    @Test
    void asksAgainOnlyForTheDocumentsNobodyIsWorkingOnAnyMore() {
        Document stuck = givenProcessingSince(Duration.ofHours(1));
        givenProcessingSince(Duration.ofSeconds(5));

        int republished = reconcile.reconcile();

        assertThat(republished).isEqualTo(1);
        Message job = rabbitTemplate.receive(DocumentsMessagingConfiguration.PROCESS_QUEUE, RECEIVE_TIMEOUT_MS);
        assertThat(job).isNotNull();
        assertThat(job.getMessageProperties().getMessageId())
                .isEqualTo(stuck.id().toString());
        assertThat(rabbitTemplate.receive(DocumentsMessagingConfiguration.PROCESS_QUEUE, SILENCE_TIMEOUT_MS))
                .isNull();
    }

    @Test
    void leavesAFinishedDocumentAloneHoweverOldItIs() {
        Document finished = givenProcessingSince(Duration.ofDays(3));
        documents.markIndexed(finished.id(), 5, null, 120);

        assertThat(reconcile.reconcile()).isZero();
        assertThat(rabbitTemplate.receive(DocumentsMessagingConfiguration.PROCESS_QUEUE, SILENCE_TIMEOUT_MS))
                .isNull();
    }
}
