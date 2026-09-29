package io.github.nicolassanchez1.technicaltestdavivienda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentCategory;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.github.nicolassanchez1.technicaltestdavivienda.support.AbstractIntegrationTest;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Guards the drift the compiler cannot see: the Java contract enums and the database CHECK
 * constraints must accept exactly the same values.
 */
class DocumentsSchemaIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    private void insertDocument(String sha256, String status, String category, String errorCode) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO documents (title, author, category, version, original_filename,
                                               mime_type, size_bytes, storage_key, sha256, status, error_code)
                        VALUES ('Guia', 'Equipo', :category, '1.0', 'guia.md',
                                'text/markdown', 128, :storageKey, :sha256, :status, :errorCode)
                        """)
                .param("category", category)
                .param("storageKey", sha256 + ".md")
                .param("sha256", sha256)
                .param("status", status)
                .param("errorCode", errorCode)
                .update();
    }

    private static String sha(char filler) {
        char[] value = new char[64];
        Arrays.fill(value, filler);
        return new String(value);
    }

    @ParameterizedTest
    @EnumSource(DocumentStatus.class)
    void acceptsEveryStatusTheDomainDefines(DocumentStatus status) {
        String errorCode = status == DocumentStatus.FAILED ? DocumentErrorCode.CORRUPT_FILE.name() : null;

        insertDocument(sha((char) ('a' + status.ordinal())), status.wireValue(), "MANUAL", errorCode);

        Integer stored = jdbcClient
                .sql("SELECT count(*) FROM documents WHERE status = :status")
                .param("status", status.wireValue())
                .query(Integer.class)
                .single();
        assertThat(stored).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(DocumentCategory.class)
    void acceptsEveryCategoryTheDomainDefines(DocumentCategory category) {
        insertDocument(sha((char) ('A' + category.ordinal())), "PROCESANDO", category.name(), null);
    }

    @Test
    void rejectsAStatusOutsideTheContract() {
        assertThatThrownBy(() -> insertDocument(sha('1'), "PENDIENTE", "MANUAL", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAFailureWithoutAReason() {
        assertThatThrownBy(() -> insertDocument(sha('2'), "ERROR", "MANUAL", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAReasonOnADocumentThatDidNotFail() {
        assertThatThrownBy(
                        () -> insertDocument(sha('3'), "PROCESANDO", "MANUAL", DocumentErrorCode.CORRUPT_FILE.name()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsADuplicateChecksum() {
        insertDocument(sha('4'), "PROCESANDO", "MANUAL", null);

        assertThatThrownBy(() -> insertDocument(sha('4'), "PROCESANDO", "OTHER", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removesChunksWhenTheirDocumentGoesAway() {
        insertDocument(sha('5'), "INDEXADO", "MANUAL", null);
        UUID documentId = jdbcClient
                .sql("SELECT id FROM documents WHERE sha256 = :sha256")
                .param("sha256", sha('5'))
                .query(UUID.class)
                .single();
        jdbcClient
                .sql(
                        """
                        INSERT INTO document_chunks (document_id, chunk_index, content, search_vector)
                        VALUES (:documentId, 0, 'Especificación técnica',
                                to_tsvector('es_unaccent', 'Especificación técnica'))
                        """)
                .param("documentId", documentId)
                .update();

        jdbcClient
                .sql("DELETE FROM documents WHERE id = :documentId")
                .param("documentId", documentId)
                .update();

        Integer remaining = jdbcClient
                .sql("SELECT count(*) FROM document_chunks WHERE document_id = :documentId")
                .param("documentId", documentId)
                .query(Integer.class)
                .single();
        assertThat(remaining).isZero();
    }
}
