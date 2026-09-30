package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.github.nicolassanchez1.technicaltestdavivienda.support.DocumentFixtures;
import io.github.nicolassanchez1.technicaltestdavivienda.support.InMemoryDocumentRepository;
import io.github.nicolassanchez1.technicaltestdavivienda.support.RecordingEventPublisher;
import io.github.nicolassanchez1.technicaltestdavivienda.support.RecordingTransactionManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The indexing rules, asserted against in-memory ports: what a repeated job does, what a file that
 * can never be read does, and what is written and announced when everything works.
 */
class IndexDocumentTest {

    private InMemoryDocumentRepository documents;
    private RecordingChunkWriter chunks;
    private RecordingEventPublisher events;
    private RecordingTransactionManager transactions;
    private StubTextExtractor extractor;
    private IndexDocument indexDocument;

    @BeforeEach
    void setUp() {
        documents = new InMemoryDocumentRepository();
        chunks = new RecordingChunkWriter();
        events = new RecordingEventPublisher();
        transactions = new RecordingTransactionManager();
        extractor = new StubTextExtractor();
        RecordProcessingFailure failures = new RecordProcessingFailure(documents, transactions.template(), events);
        indexDocument = new IndexDocument(
                documents,
                chunks,
                new StubFileStorage(),
                extractor,
                new DocumentChunker(),
                failures,
                transactions.template(),
                events);
    }

    private Document givenProcessingPdf() {
        return documents.given(
                DocumentFixtures.processing("Especificación técnica", DocumentFixtures.storageKey("pdf")));
    }

    private static ExtractedDocument twoPagesOfFour() {
        return ExtractedDocument.paged(
                List.of(
                        ExtractedUnit.onPage(1, "Especificación técnica del buscador."),
                        ExtractedUnit.onPage(3, "El índice se reconstruye después de cada modificación.")),
                4);
    }

    private List<DocumentStatusChanged> announced() {
        return events.eventsOfType(DocumentStatusChanged.class);
    }

    @Test
    void ignoresAJobForADocumentThatNoLongerExists() {
        indexDocument.index(UUID.randomUUID());

        assertThat(extractor.calls).isZero();
        assertThat(chunks.inserted).isEmpty();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void ignoresARepeatedJobForADocumentThatAlreadyFinished() {
        Document document = givenProcessingPdf();
        documents.markIndexed(document.id(), 3, 4, 120);

        indexDocument.index(document.id());

        assertThat(extractor.calls).isZero();
        assertThat(chunks.inserted).isEmpty();
        assertThat(events.published()).isEmpty();
        assertThat(documents.require(document.id()).chunkCount()).isEqualTo(3);
    }

    @Test
    void writesTheChunksAndAnnouncesTheDocumentExactlyOnce() {
        Document document = givenProcessingPdf();
        extractor.returns(twoPagesOfFour());

        indexDocument.index(document.id());

        Document indexed = documents.require(document.id());
        assertThat(indexed.status()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(indexed.chunkCount()).isEqualTo(2);
        // The page count counts the pages of the file, not the ones that carried text.
        assertThat(indexed.pageCount()).isEqualTo(4);
        assertThat(indexed.processingMs()).isNotNull();
        assertThat(indexed.errorCode()).isNull();

        assertThat(chunks.deleted).containsExactly(document.id());
        assertThat(chunks.inserted).hasSize(2);
        assertThat(chunks.inserted.stream().map(DocumentChunkDraft::chunkIndex)).containsExactly(0, 1);
        assertThat(chunks.inserted.stream().map(DocumentChunkDraft::page)).containsExactly(1, 3);

        assertThat(announced()).singleElement().satisfies(event -> {
            assertThat(event.documentId()).isEqualTo(document.id());
            assertThat(event.status()).isEqualTo(DocumentStatus.INDEXED);
            assertThat(event.errorCode()).isNull();
        });
        assertThat(transactions.commits()).isEqualTo(1);
    }

    @Test
    void removesTheChunksOfAPreviousRunBeforeWritingTheNewOnes() {
        Document document = givenProcessingPdf();
        extractor.returns(twoPagesOfFour());

        indexDocument.index(document.id());

        assertThat(chunks.calls).containsExactly("delete", "insert");
    }

    @Test
    void recordsTheErrorCodeOfADeterministicFailureAndRefusesToRetry() {
        Document document = givenProcessingPdf();
        extractor.failsWith(new ExtractionFailedException(
                DocumentErrorCode.PDF_NO_TEXT_LAYER, "None of the 4 page(s) of documento.pdf carries a text layer."));

        assertThatThrownBy(() -> indexDocument.index(document.id()))
                .isInstanceOf(NonRetryableProcessingException.class)
                .satisfies(failure -> assertThat(((NonRetryableProcessingException) failure).errorCode())
                        .isEqualTo(DocumentErrorCode.PDF_NO_TEXT_LAYER));

        Document failed = documents.require(document.id());
        assertThat(failed.status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo(DocumentErrorCode.PDF_NO_TEXT_LAYER);
        assertThat(failed.errorMessage()).contains("text layer");
        assertThat(failed.indexedAt()).isNull();

        assertThat(chunks.inserted).isEmpty();
        assertThat(announced()).singleElement().satisfies(event -> {
            assertThat(event.status()).isEqualTo(DocumentStatus.FAILED);
            assertThat(event.errorCode()).isEqualTo(DocumentErrorCode.PDF_NO_TEXT_LAYER);
        });
    }

    @Test
    void leavesADocumentProcessingWhenTheFailureMayStillGoAwayOnTheNextAttempt() {
        Document document = givenProcessingPdf();
        extractor.failsWith(new UncheckedIOException(new IOException("The shared volume has not published the file")));

        assertThatThrownBy(() -> indexDocument.index(document.id())).isInstanceOf(UncheckedIOException.class);

        Document untouched = documents.require(document.id());
        assertThat(untouched.status()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(untouched.errorCode()).isNull();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void rollsBackAndAnnouncesNothingWhenAnotherConsumerFinishedTheDocumentFirst() {
        Document document = givenProcessingPdf();
        extractor.returns(twoPagesOfFour());
        // The race this guards against: the other consumer commits while this one is still parsing.
        extractor.onExtract(() -> documents.markIndexed(document.id(), 9, 4, 42));

        indexDocument.index(document.id());

        assertThat(announced()).isEmpty();
        assertThat(transactions.rollbacks()).isEqualTo(1);
        assertThat(transactions.commits()).isZero();
        assertThat(documents.require(document.id()).chunkCount()).isEqualTo(9);
    }

    private static final class RecordingChunkWriter implements DocumentChunkWriter {
        private final List<UUID> deleted = new ArrayList<>();
        private final List<DocumentChunkDraft> inserted = new ArrayList<>();
        private final List<String> calls = new ArrayList<>();

        @Override
        public int deleteByDocument(UUID documentId) {
            deleted.add(documentId);
            calls.add("delete");
            return 0;
        }

        @Override
        public void insertAll(Document document, List<DocumentChunkDraft> chunks) {
            inserted.addAll(chunks);
            calls.add("insert");
        }
    }

    private static final class StubTextExtractor implements TextExtractor {
        private ExtractedDocument extracted;
        private RuntimeException failure;
        private Runnable duringExtraction = () -> {};
        private int calls;

        void returns(ExtractedDocument document) {
            this.extracted = document;
        }

        void failsWith(RuntimeException failure) {
            this.failure = failure;
        }

        void onExtract(Runnable duringExtraction) {
            this.duringExtraction = duringExtraction;
        }

        @Override
        public ExtractedDocument extract(Path file, UploadFileType fileType) {
            calls++;
            duringExtraction.run();
            if (failure != null) {
                throw failure;
            }
            return extracted;
        }
    }

    private static final class StubFileStorage implements FileStorage {

        @Override
        public String store(InputStream content, String extension) {
            throw new UnsupportedOperationException("Indexing never stores a file");
        }

        @Override
        public Path resolve(String storageKey) {
            return Path.of("/tmp/storage", storageKey);
        }

        @Override
        public void delete(String storageKey) {
            throw new UnsupportedOperationException("Indexing never deletes a file");
        }
    }
}
