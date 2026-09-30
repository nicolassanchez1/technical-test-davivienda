package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.github.nicolassanchez1.technicaltestdavivienda.support.DocumentFixtures;
import io.github.nicolassanchez1.technicaltestdavivienda.support.InMemoryDocumentRepository;
import io.github.nicolassanchez1.technicaltestdavivienda.support.RecordingEventPublisher;
import io.github.nicolassanchez1.technicaltestdavivienda.support.RecordingTransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The failure path is written down exactly once, and never over a document that already finished. */
class RecordProcessingFailureTest {

    private InMemoryDocumentRepository documents;
    private RecordingEventPublisher events;
    private RecordProcessingFailure failures;

    @BeforeEach
    void setUp() {
        documents = new InMemoryDocumentRepository();
        events = new RecordingEventPublisher();
        failures = new RecordProcessingFailure(documents, new RecordingTransactionManager().template(), events);
    }

    private Document givenProcessing() {
        return documents.given(DocumentFixtures.processing("Manual de operación", DocumentFixtures.storageKey("txt")));
    }

    @Test
    void writesTheReasonDownAndAnnouncesIt() {
        Document document = givenProcessing();

        boolean recorded =
                failures.record(document.id(), DocumentErrorCode.CORRUPT_FILE, "The PDF could not be parsed");

        assertThat(recorded).isTrue();
        Document failed = documents.require(document.id());
        assertThat(failed.status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo(DocumentErrorCode.CORRUPT_FILE);
        assertThat(failed.errorMessage()).isEqualTo("The PDF could not be parsed");
        assertThat(events.eventsOfType(DocumentStatusChanged.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.status()).isEqualTo(DocumentStatus.FAILED);
                    assertThat(event.errorCode()).isEqualTo(DocumentErrorCode.CORRUPT_FILE);
                });
    }

    @Test
    void leavesADocumentThatAlreadyFinishedAloneAndAnnouncesNothing() {
        Document document = givenProcessing();
        documents.markIndexed(document.id(), 4, null, 90);

        boolean recorded = failures.record(document.id(), DocumentErrorCode.PROCESSING_FAILED, "Attempts exhausted");

        assertThat(recorded).isFalse();
        assertThat(documents.require(document.id()).status()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(events.eventsOfType(DocumentStatusChanged.class)).isEmpty();
    }

    @Test
    void shortensAReasonThatWouldNotFitABadge() {
        Document document = givenProcessing();

        failures.record(document.id(), DocumentErrorCode.PROCESSING_FAILED, "x".repeat(1_000));

        String message = documents.require(document.id()).errorMessage();
        assertThat(message)
                .hasSize(RecordProcessingFailure.MAX_ERROR_MESSAGE_LENGTH)
                .endsWith("…");
    }

    @Test
    void alwaysStoresAReason() {
        Document document = givenProcessing();

        failures.record(document.id(), DocumentErrorCode.PROCESSING_FAILED, "   ");

        assertThat(documents.require(document.id()).errorMessage()).isNotBlank();
    }
}
