package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentRepository;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentCategory;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DuplicateDocumentException;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Explicit SQL over {@link JdbcClient}: every statement is readable and can be run through EXPLAIN. */
@Repository
public class JdbcDocumentRepository implements DocumentRepository {

    private static final String SHA256_UNIQUE_CONSTRAINT = "documents_sha256_unique";
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private static final String COLUMNS = "id, title, author, category, tags, version, original_filename, "
            + "mime_type, size_bytes, storage_key, sha256, status, error_code, error_message, "
            + "page_count, chunk_count, processing_ms, created_at, updated_at, indexed_at";

    private static final String INSERT_DOCUMENT = "INSERT INTO documents (" + COLUMNS + ") VALUES ("
            + ":id, :title, :author, :category, :tags, :version, :originalFilename, "
            + ":mimeType, :sizeBytes, :storageKey, :sha256, :status, :errorCode, :errorMessage, "
            + ":pageCount, :chunkCount, :processingMs, :createdAt, :updatedAt, :indexedAt) "
            + "RETURNING " + COLUMNS;

    private static final String SELECT_BY_ID = "SELECT " + COLUMNS + " FROM documents WHERE id = :id";

    private static final String SELECT_ID_BY_SHA256 = "SELECT id FROM documents WHERE sha256 = :sha256";

    // The status filter is a separate statement on purpose: a nullable predicate such as
    // "WHERE (CAST(:status AS text) IS NULL OR status = :status)" would cost the index scan.
    private static final String SELECT_NEWEST =
            "SELECT " + COLUMNS + " FROM documents ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset";

    private static final String SELECT_NEWEST_BY_STATUS = "SELECT " + COLUMNS
            + " FROM documents WHERE status = :status"
            + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset";

    private static final String COUNT_ALL = "SELECT count(*) FROM documents";

    private static final String COUNT_BY_STATUS = "SELECT count(*) FROM documents WHERE status = :status";

    private static final RowMapper<Document> DOCUMENT_MAPPER = (row, rowNumber) -> new Document(
            row.getObject("id", UUID.class),
            row.getString("title"),
            row.getString("author"),
            DocumentCategory.valueOf(row.getString("category")),
            tagsOf(row),
            row.getString("version"),
            row.getString("original_filename"),
            row.getString("mime_type"),
            row.getLong("size_bytes"),
            row.getString("storage_key"),
            row.getString("sha256"),
            DocumentStatus.fromWireValue(row.getString("status")),
            errorCodeOf(row),
            row.getString("error_message"),
            row.getObject("page_count", Integer.class),
            row.getObject("chunk_count", Integer.class),
            row.getObject("processing_ms", Long.class),
            instantOf(row, "created_at"),
            instantOf(row, "updated_at"),
            instantOf(row, "indexed_at"));

    private final JdbcClient jdbcClient;

    public JdbcDocumentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Document save(Document document) {
        try {
            return jdbcClient
                    .sql(INSERT_DOCUMENT)
                    .param("id", document.id())
                    .param("title", document.title())
                    .param("author", document.author())
                    .param("category", document.category().name())
                    .param("tags", textArray(document.tags()))
                    .param("version", document.version())
                    .param("originalFilename", document.originalFilename())
                    .param("mimeType", document.mimeType())
                    .param("sizeBytes", document.sizeBytes())
                    .param("storageKey", document.storageKey())
                    .param("sha256", document.sha256())
                    .param("status", document.status().wireValue())
                    .param(
                            "errorCode",
                            document.errorCode() == null
                                    ? null
                                    : document.errorCode().name())
                    .param("errorMessage", document.errorMessage())
                    .param("pageCount", document.pageCount())
                    .param("chunkCount", document.chunkCount())
                    .param("processingMs", document.processingMs())
                    .param("createdAt", timestampOf(document.createdAt()))
                    .param("updatedAt", timestampOf(document.updatedAt()))
                    .param("indexedAt", timestampOf(document.indexedAt()))
                    .query(DOCUMENT_MAPPER)
                    .single();
        } catch (DataIntegrityViolationException violation) {
            throw checksumClash(violation, document.sha256());
        }
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return jdbcClient
                .sql(SELECT_BY_ID)
                .param("id", id)
                .query(DOCUMENT_MAPPER)
                .optional();
    }

    @Override
    public Optional<UUID> findIdBySha256(String sha256) {
        return jdbcClient
                .sql(SELECT_ID_BY_SHA256)
                .param("sha256", sha256)
                .query(UUID.class)
                .optional();
    }

    @Override
    public List<Document> findAll(DocumentStatus statusOrNull, int limit, int offset) {
        if (statusOrNull == null) {
            return jdbcClient
                    .sql(SELECT_NEWEST)
                    .param("limit", limit)
                    .param("offset", offset)
                    .query(DOCUMENT_MAPPER)
                    .list();
        }
        return jdbcClient
                .sql(SELECT_NEWEST_BY_STATUS)
                .param("status", statusOrNull.wireValue())
                .param("limit", limit)
                .param("offset", offset)
                .query(DOCUMENT_MAPPER)
                .list();
    }

    @Override
    public long countAll(DocumentStatus statusOrNull) {
        if (statusOrNull == null) {
            return jdbcClient.sql(COUNT_ALL).query(Long.class).single();
        }
        return jdbcClient
                .sql(COUNT_BY_STATUS)
                .param("status", statusOrNull.wireValue())
                .query(Long.class)
                .single();
    }

    /**
     * The unique index on sha256 is what stops the same file from being stored and processed twice.
     * A clash on it is reported with the id that already holds the content; every other integrity
     * failure propagates untouched.
     */
    private RuntimeException checksumClash(DataIntegrityViolationException violation, String sha256) {
        Throwable cause = violation.getMostSpecificCause();
        boolean clashed = cause instanceof SQLException failure
                && UNIQUE_VIOLATION_SQL_STATE.equals(failure.getSQLState())
                && String.valueOf(failure.getMessage()).contains(SHA256_UNIQUE_CONSTRAINT);
        if (!clashed) {
            return violation;
        }
        return findIdBySha256(sha256)
                .<RuntimeException>map(DuplicateDocumentException::new)
                .orElse(violation);
    }

    /** Binds the tags on the same connection the statement runs on, as a real PostgreSQL text[]. */
    private static SqlTypeValue textArray(List<String> values) {
        String[] tags = values.toArray(String[]::new);
        return (statement, index, sqlType, typeName) ->
                statement.setArray(index, statement.getConnection().createArrayOf("text", tags));
    }

    private static List<String> tagsOf(ResultSet row) throws SQLException {
        Array stored = row.getArray("tags");
        if (stored == null) {
            return List.of();
        }
        return List.of((String[]) stored.getArray());
    }

    private static DocumentErrorCode errorCodeOf(ResultSet row) throws SQLException {
        String stored = row.getString("error_code");
        return stored == null ? null : DocumentErrorCode.valueOf(stored);
    }

    private static Instant instantOf(ResultSet row, String column) throws SQLException {
        OffsetDateTime stored = row.getObject(column, OffsetDateTime.class);
        return stored == null ? null : stored.toInstant();
    }

    private static OffsetDateTime timestampOf(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
