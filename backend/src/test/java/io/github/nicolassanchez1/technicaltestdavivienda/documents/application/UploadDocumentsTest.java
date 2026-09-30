package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentCategory;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentMetadata;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.github.nicolassanchez1.technicaltestdavivienda.shared.config.AppProperties;
import io.github.nicolassanchez1.technicaltestdavivienda.support.InMemoryDocumentRepository;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the use case through in-memory ports, so the ordering guarantees are asserted without a
 * database, a filesystem or a broker being involved.
 */
class UploadDocumentsTest {

    private static final byte[] MARKDOWN = "# Guia\n\nContenido tecnico.".getBytes(StandardCharsets.UTF_8);

    private InMemoryDocumentRepository documents;
    private InMemoryFileStorage storage;
    private RecordingEventPublisher events;
    private UploadDocuments uploadDocuments;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(Path.of("/tmp/storage"), 20, 3, 900, 50, 4, 10, 15000);
        documents = new InMemoryDocumentRepository();
        storage = new InMemoryFileStorage();
        events = new RecordingEventPublisher();
        uploadDocuments = new UploadDocuments(new UploadValidator(properties), documents, storage, events, properties);
    }

    private static UploadedFile file(String filename, byte[] content) {
        return new UploadedFile(filename, content.length, () -> new ByteArrayInputStream(content));
    }

    private static DocumentMetadata metadata(String title) {
        return new DocumentMetadata(title, "Equipo", DocumentCategory.MANUAL, List.of("infra"), "1.0");
    }

    @Test
    void storesAFileAsProcessingAndAsksForItToBeIndexed() {
        List<Document> stored = uploadDocuments.upload(List.of(file("guia.md", MARKDOWN)), List.of(metadata("Guia")));

        assertThat(stored).hasSize(1);
        Document document = stored.getFirst();
        assertThat(document.status()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(document.originalFilename()).isEqualTo("guia.md");
        assertThat(document.mimeType()).isEqualTo("text/markdown");
        assertThat(document.errorCode()).isNull();
        assertThat(storage.stored).hasSize(1);
        assertThat(events.requestedDocumentIds()).containsExactly(document.id());
    }

    @Test
    void namesTheStoredFileAfterItsIdentifierAndNeverAfterTheUpload() {
        uploadDocuments.upload(List.of(file("../../etc/passwd.md", MARKDOWN)), List.of(metadata("Guia")));

        assertThat(storage.stored.keySet()).allSatisfy(key -> assertThat(key).doesNotContain("passwd"));
    }

    @Test
    void asksForOneJobPerAcceptedDocument() {
        List<Document> stored = uploadDocuments.upload(
                List.of(file("a.md", MARKDOWN), file("b.txt", "texto".getBytes(StandardCharsets.UTF_8))),
                List.of(metadata("A"), metadata("B")));

        assertThat(events.requestedDocumentIds())
                .containsExactlyElementsOf(stored.stream().map(Document::id).toList());
    }

    @Test
    void refusesAnEmptyBatch() {
        assertThatThrownBy(() -> uploadDocuments.upload(List.of(), List.of()))
                .isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void refusesABatchBeyondTheConfiguredCeiling() {
        List<UploadedFile> files =
                List.of(file("a.md", MARKDOWN), file("b.md", MARKDOWN), file("c.md", MARKDOWN), file("d.md", MARKDOWN));

        assertThatThrownBy(() -> uploadDocuments.upload(
                        files, List.of(metadata("A"), metadata("B"), metadata("C"), metadata("D"))))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("at most 3");
    }

    @Test
    void refusesMetadataThatDoesNotLineUpWithTheFiles() {
        assertThatThrownBy(() -> uploadDocuments.upload(
                        List.of(file("a.md", MARKDOWN), file("b.md", MARKDOWN)), List.of(metadata("A"))))
                .isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void failsTheWholeBatchWhenOneFileBreaksARuleAndStoresNothing() {
        List<UploadedFile> files = List.of(file("good.md", MARKDOWN), file("bad.exe", MARKDOWN));

        assertThatThrownBy(() -> uploadDocuments.upload(files, List.of(metadata("A"), metadata("B"))))
                .isInstanceOf(InvalidUploadException.class)
                .satisfies(failure -> {
                    List<UploadFileError> errors = ((InvalidUploadException) failure).errors();
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().index()).isEqualTo(1);
                    assertThat(errors.getFirst().rule()).isEqualTo(UploadRule.EXTENSION_ALLOWLIST);
                    assertThat(errors.getFirst().errorCode()).isEqualTo(DocumentErrorCode.UNSUPPORTED_FORMAT);
                });

        assertThat(storage.stored).isEmpty();
        assertThat(documents.saved()).isEmpty();
        assertThat(events.requestedDocumentIds()).isEmpty();
    }

    @Test
    void reportsEveryRejectedFileOfABatchAtOnce() {
        List<UploadedFile> files = List.of(file("bad.exe", MARKDOWN), file("empty.md", new byte[0]));

        assertThatThrownBy(() -> uploadDocuments.upload(files, List.of(metadata("A"), metadata("B"))))
                .isInstanceOf(InvalidUploadException.class)
                .satisfies(failure ->
                        assertThat(((InvalidUploadException) failure).errors()).hasSize(2));
    }

    @Test
    void pointsADuplicateAtTheDocumentThatAlreadyHoldsTheContent() {
        Document first = uploadDocuments
                .upload(List.of(file("guia.md", MARKDOWN)), List.of(metadata("Guia")))
                .getFirst();
        events.reset();

        assertThatThrownBy(
                        () -> uploadDocuments.upload(List.of(file("copia.md", MARKDOWN)), List.of(metadata("Copia"))))
                .isInstanceOf(InvalidUploadException.class)
                .satisfies(failure -> {
                    UploadFileError error =
                            ((InvalidUploadException) failure).errors().getFirst();
                    assertThat(error.rule()).isEqualTo(UploadRule.UNIQUE_CHECKSUM);
                    assertThat(error.existingDocumentId()).isEqualTo(first.id());
                });

        assertThat(documents.saved()).hasSize(1);
        assertThat(events.requestedDocumentIds()).isEmpty();
    }

    private static final class InMemoryFileStorage implements FileStorage {
        private final Map<String, byte[]> stored = new LinkedHashMap<>();

        @Override
        public String store(InputStream content, String extension) {
            String key = UUID.randomUUID() + "." + extension;
            try (InputStream source = content) {
                stored.put(key, source.readAllBytes());
            } catch (Exception cause) {
                throw new IllegalStateException(cause);
            }
            return key;
        }

        @Override
        public Path resolve(String storageKey) {
            return Path.of("/tmp/storage", storageKey);
        }

        @Override
        public void delete(String storageKey) {
            stored.remove(storageKey);
        }
    }

    private static final class RecordingEventPublisher
            implements org.springframework.context.ApplicationEventPublisher {
        private final List<UUID> requested = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            if (event instanceof DocumentProcessingRequested requestedEvent) {
                requested.add(requestedEvent.documentId());
            }
        }

        List<UUID> requestedDocumentIds() {
            return List.copyOf(requested);
        }

        void reset() {
            requested.clear();
        }
    }
}
