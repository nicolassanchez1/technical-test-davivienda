package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentChunkRepository;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The viewer's read path. The predicate matches the leading columns of
 * {@code document_chunks (document_id, chunk_index)}, so the statement is an index range scan
 * bounded by the page size no matter how far into a document the reader has scrolled.
 *
 * <p>Only reads live here: the worker writes chunks together with their {@code tsvector} inside its
 * own transaction.
 */
@Repository
public class JdbcDocumentChunkRepository implements DocumentChunkRepository {

    private static final String SELECT_FROM_CHUNK_INDEX =
            """
            SELECT id, document_id, chunk_index, page, heading, content
            FROM document_chunks
            WHERE document_id = :documentId AND chunk_index >= :fromChunkIndex
            ORDER BY chunk_index
            LIMIT :limit
            """;

    private static final RowMapper<DocumentChunk> CHUNK_MAPPER = (row, rowNumber) -> new DocumentChunk(
            row.getLong("id"),
            row.getObject("document_id", UUID.class),
            row.getInt("chunk_index"),
            row.getObject("page", Integer.class),
            row.getString("heading"),
            row.getString("content"));

    private final JdbcClient jdbcClient;

    public JdbcDocumentChunkRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<DocumentChunk> findByDocument(UUID documentId, int fromChunkIndex, int limit) {
        return jdbcClient
                .sql(SELECT_FROM_CHUNK_INDEX)
                .param("documentId", documentId)
                .param("fromChunkIndex", fromChunkIndex)
                .param("limit", limit)
                .query(CHUNK_MAPPER)
                .list();
    }
}
