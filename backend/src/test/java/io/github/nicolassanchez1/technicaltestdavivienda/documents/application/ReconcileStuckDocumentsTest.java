package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.shared.config.AppProperties;
import io.github.nicolassanchez1.technicaltestdavivienda.support.DocumentFixtures;
import io.github.nicolassanchez1.technicaltestdavivienda.support.InMemoryDocumentRepository;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Only the documents nobody is working on any more are asked for again, and only so many at a time. */
class ReconcileStuckDocumentsTest {

    private static final int STUCK_AFTER_MINUTES = 10;

    private InMemoryDocumentRepository documents;
    private RecordingJobPublisher jobs;
    private ReconcileStuckDocuments reconcile;

    @BeforeEach
    void setUp() {
        documents = new InMemoryDocumentRepository();
        jobs = new RecordingJobPublisher();
        AppProperties properties =
                new AppProperties(Path.of("/tmp/storage"), 20, 10, 900, 50, 4, STUCK_AFTER_MINUTES, 15000, 3_600_000L);
        reconcile = new ReconcileStuckDocuments(documents, jobs, properties);
    }

    private Document givenProcessingSince(Duration age) {
        return documents.given(DocumentFixtures.processing(
                "Especificación técnica",
                DocumentFixtures.storageKey("md"),
                Instant.now().minus(age)));
    }

    @Test
    void asksAgainForEveryDocumentLeftProcessingForTooLong() {
        Document stuck = givenProcessingSince(Duration.ofMinutes(STUCK_AFTER_MINUTES + 1));
        givenProcessingSince(Duration.ofMinutes(STUCK_AFTER_MINUTES - 1));

        int republished = reconcile.reconcile();

        assertThat(republished).isEqualTo(1);
        assertThat(jobs.published).containsExactly(stuck.id());
    }

    @Test
    void leavesADocumentThatAlreadyFinishedAlone() {
        Document finished = givenProcessingSince(Duration.ofHours(2));
        documents.markIndexed(finished.id(), 2, null, 30);

        assertThat(reconcile.reconcile()).isZero();
        assertThat(jobs.published).isEmpty();
    }

    @Test
    void publishesNothingWhenTheQueueIsKeepingUp() {
        givenProcessingSince(Duration.ofSeconds(5));

        assertThat(reconcile.reconcile()).isZero();
        assertThat(jobs.published).isEmpty();
    }

    @Test
    void boundsHowManyDocumentsOnePassRepublishes() {
        for (int index = 0; index < ReconcileStuckDocuments.MAX_DOCUMENTS_PER_PASS + 5; index++) {
            givenProcessingSince(Duration.ofHours(1));
        }

        assertThat(reconcile.reconcile()).isEqualTo(ReconcileStuckDocuments.MAX_DOCUMENTS_PER_PASS);
        assertThat(jobs.published).hasSize(ReconcileStuckDocuments.MAX_DOCUMENTS_PER_PASS);
    }

    private static final class RecordingJobPublisher implements JobPublisher {
        private final List<UUID> published = new ArrayList<>();

        @Override
        public void publishProcessingJob(UUID documentId) {
            published.add(documentId);
        }
    }
}
