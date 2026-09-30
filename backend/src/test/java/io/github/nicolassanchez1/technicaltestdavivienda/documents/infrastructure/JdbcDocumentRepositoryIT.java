package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentRepository;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentCategory;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DuplicateDocumentException;
import io.github.nicolassanchez1.technicaltestdavivienda.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The adapter is asserted against the real schema: array columns and CHECK constraints included. */
class JdbcDocumentRepositoryIT extends AbstractIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-29T10:15:30.123456Z");

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private JdbcClient jdbcClient;

    private static String checksum(char filler) {
        return String.valueOf(filler).repeat(64);
    }

    private static Document processing(String sha256, Instant createdAt) {
        return new Document(
                UUID.randomUUID(),
                "Manual de operación",
                "Equipo de plataforma",
                DocumentCategory.MANUAL,
                List.of(),
                "1.0",
                "manual.txt",
                "text/plain",
                1024,
                UUID.randomUUID() + ".txt",
                sha256,
                DocumentStatus.PROCESSING,
                null,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt,
                null);
    }

    private static Document indexed(String sha256, Instant createdAt) {
        return new Document(
                UUID.randomUUID(),
                "Guía de arquitectura hexagonal",
                "Equipo de plataforma",
                DocumentCategory.ARCHITECTURE_GUIDE,
                List.of("arquitectura", "hexagonal", "adr"),
                "2.1",
                "guía de arquitectura.md",
                "text/markdown",
                4096,
                UUID.randomUUID() + ".md",
                sha256,
                DocumentStatus.INDEXED,
                null,
                null,
                12,
                34,
                456L,
                createdAt,
                createdAt.plusSeconds(2),
                createdAt.plusSeconds(3));
    }

    @BeforeEach
    void clearDocuments() {
        jdbcClient.sql("DELETE FROM documents").update();
    }

    @Test
    void storesAndReadsBackEveryColumnIncludingTheTagArray() {
        Document document = indexed(checksum('a'), CREATED_AT);

        Document saved = documents.save(document);

        assertThat(saved).isEqualTo(document);
        assertThat(documents.findById(document.id())).hasValue(document);
        assertThat(saved.tags()).containsExactly("arquitectura", "hexagonal", "adr");
    }

    @Test
    void leavesTheNullableColumnsEmptyForADocumentStillBeingProcessed() {
        Document document = processing(checksum('b'), CREATED_AT);

        Document saved = documents.save(document);

        assertThat(saved.tags()).isEmpty();
        assertThat(saved.errorCode()).isNull();
        assertThat(saved.errorMessage()).isNull();
        assertThat(saved.pageCount()).isNull();
        assertThat(saved.chunkCount()).isNull();
        assertThat(saved.processingMs()).isNull();
        assertThat(saved.indexedAt()).isNull();
        assertThat(documents.findById(document.id())).hasValue(document);
    }

    @Test
    void storesAFailureTogetherWithItsReason() {
        Document failed = new Document(
                UUID.randomUUID(),
                "Escaneo sin capa de texto",
                "Equipo de plataforma",
                DocumentCategory.SPECIFICATION,
                List.of("pdf"),
                "1.0",
                "escaneo.pdf",
                "application/pdf",
                8192,
                UUID.randomUUID() + ".pdf",
                checksum('c'),
                DocumentStatus.FAILED,
                DocumentErrorCode.PDF_NO_TEXT_LAYER,
                "The PDF carries no extractable text.",
                7,
                null,
                980L,
                CREATED_AT,
                CREATED_AT,
                null);

        documents.save(failed);

        assertThat(documents.findById(failed.id())).hasValue(failed);
    }

    @Test
    void returnsEmptyForAnUnknownId() {
        assertThat(documents.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findsTheIdBehindAStoredChecksum() {
        Document document = processing(checksum('d'), CREATED_AT);
        documents.save(document);

        assertThat(documents.findIdBySha256(checksum('d'))).hasValue(document.id());
        assertThat(documents.findIdBySha256(checksum('e'))).isEmpty();
    }

    @Test
    void listsDocumentsNewestFirstAndPaginates() {
        Document oldest = documents.save(processing(checksum('f'), CREATED_AT));
        Document middle = documents.save(processing(checksum('g'), CREATED_AT.plusSeconds(60)));
        Document newest = documents.save(processing(checksum('h'), CREATED_AT.plusSeconds(120)));

        assertThat(documents.findAll(null, 2, 0)).containsExactly(newest, middle);
        assertThat(documents.findAll(null, 2, 2)).containsExactly(oldest);
        assertThat(documents.findAll(null, 10, 3)).isEmpty();
    }

    @Test
    void filtersByStatusWithoutLosingThePagination() {
        Document firstProcessing = documents.save(processing(checksum('i'), CREATED_AT));
        Document secondProcessing = documents.save(processing(checksum('j'), CREATED_AT.plusSeconds(60)));
        documents.save(indexed(checksum('k'), CREATED_AT.plusSeconds(120)));

        assertThat(documents.findAll(DocumentStatus.PROCESSING, 10, 0))
                .containsExactly(secondProcessing, firstProcessing);
        assertThat(documents.findAll(DocumentStatus.PROCESSING, 1, 1)).containsExactly(firstProcessing);
        assertThat(documents.findAll(DocumentStatus.INDEXED, 10, 0)).hasSize(1);
        assertThat(documents.findAll(DocumentStatus.FAILED, 10, 0)).isEmpty();
    }

    @Test
    void countsEveryDocumentAndEachStatusSeparately() {
        documents.save(processing(checksum('l'), CREATED_AT));
        documents.save(processing(checksum('m'), CREATED_AT.plusSeconds(60)));
        documents.save(indexed(checksum('n'), CREATED_AT.plusSeconds(120)));

        assertThat(documents.countAll(null)).isEqualTo(3);
        assertThat(documents.countAll(DocumentStatus.PROCESSING)).isEqualTo(2);
        assertThat(documents.countAll(DocumentStatus.INDEXED)).isEqualTo(1);
        assertThat(documents.countAll(DocumentStatus.FAILED)).isZero();
    }

    @Test
    void reportsADuplicateChecksumWithTheIdThatAlreadyHoldsIt() {
        Document first = documents.save(processing(checksum('o'), CREATED_AT));
        Document again = processing(checksum('o'), CREATED_AT.plusSeconds(60));

        assertThatExceptionOfType(DuplicateDocumentException.class)
                .isThrownBy(() -> documents.save(again))
                .satisfies(failure -> assertThat(failure.existingDocumentId()).isEqualTo(first.id()));
        assertThat(documents.countAll(null)).isEqualTo(1);
    }

    @Test
    void movesADocumentToIndexedOnlyWhileItIsStillBeingProcessed() {
        Document document = documents.save(processing(checksum('p'), CREATED_AT));

        assertThat(documents.markIndexed(document.id(), 7, 3, 250)).isTrue();

        Document indexed = documents.findById(document.id()).orElseThrow();
        assertThat(indexed.status()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(indexed.chunkCount()).isEqualTo(7);
        assertThat(indexed.pageCount()).isEqualTo(3);
        assertThat(indexed.processingMs()).isEqualTo(250);
        assertThat(indexed.indexedAt()).isNotNull();
        assertThat(indexed.updatedAt()).isNotEqualTo(CREATED_AT);

        // The second consumer of a repeated job updates nothing and is told so.
        assertThat(documents.markIndexed(document.id(), 99, 9, 1)).isFalse();
        assertThat(documents.findById(document.id()).orElseThrow().chunkCount()).isEqualTo(7);
    }

    @Test
    void recordsAFailureWithTheReasonTheSchemaInsistsOn() {
        Document document = documents.save(processing(checksum('q'), CREATED_AT));

        assertThat(documents.markFailed(document.id(), DocumentErrorCode.PDF_NO_TEXT_LAYER, "No text layer"))
                .isTrue();

        Document failed = documents.findById(document.id()).orElseThrow();
        assertThat(failed.status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo(DocumentErrorCode.PDF_NO_TEXT_LAYER);
        assertThat(failed.errorMessage()).isEqualTo("No text layer");
        assertThat(failed.indexedAt()).isNull();

        // A document that already finished keeps the outcome it reached.
        assertThat(documents.markFailed(document.id(), DocumentErrorCode.PROCESSING_FAILED, "Too late"))
                .isFalse();
    }
}
