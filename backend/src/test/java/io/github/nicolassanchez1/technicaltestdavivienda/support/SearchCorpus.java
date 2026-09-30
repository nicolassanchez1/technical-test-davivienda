package io.github.nicolassanchez1.technicaltestdavivienda.support;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentCategory;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Indexed documents written straight to the tables, the way the worker leaves them.
 *
 * <p>The vector is built with the same weighting as {@code JdbcDocumentChunkWriter}: the title
 * weighs A, the tags with the category and the author weigh B, and the chunk's own text weighs C.
 * A fixture that weighed them differently would let a search test pass against data production
 * never produces.
 */
public final class SearchCorpus {

    private static final String INSERT_DOCUMENT =
            """
            INSERT INTO documents (id, title, author, category, tags, version, original_filename, mime_type,
                                   size_bytes, storage_key, sha256, status, error_code, chunk_count, indexed_at)
            VALUES (:id, :title, :author, :category, :tags, '1.0', 'documento.md', 'text/markdown',
                    2048, :storageKey, :sha256, :status, :errorCode, :chunkCount, :indexedAt)
            """;

    private static final String INSERT_CHUNK =
            """
            INSERT INTO document_chunks (document_id, chunk_index, page, heading, content, search_vector)
            SELECT :documentId, :chunkIndex, :page, :heading, :content,
                   setweight(to_tsvector('es_unaccent', :title), 'A')
                       || setweight(to_tsvector('es_unaccent', :metadata), 'B')
                       || setweight(to_tsvector('es_unaccent', :content), 'C')
            """;

    /**
     * Enough documents, wide enough, that the planner has a reason to reach for the GIN index
     * instead of reading the table. None of them carries the words the assertions search for.
     */
    private static final String INSERT_FILLER =
            """
            WITH filler AS (
                INSERT INTO documents (title, author, category, tags, version, original_filename, mime_type,
                                       size_bytes, storage_key, sha256, status, chunk_count, indexed_at)
                SELECT 'Nota operativa ' || n, 'Area de soporte', 'OTHER', ARRAY['relleno'], '1.0',
                       'nota.md', 'text/markdown', 1024, gen_random_uuid() || '.md',
                       md5(n::text) || md5((n + 7)::text), :status, :chunksPerDocument, now()
                FROM generate_series(1, :documents) AS n
                RETURNING id, title
            )
            INSERT INTO document_chunks (document_id, chunk_index, page, heading, content, search_vector)
            SELECT f.id, c.i, c.i + 1, 'Seccion ' || c.i, body.text,
                   setweight(to_tsvector('es_unaccent', f.title), 'A')
                       || setweight(to_tsvector('es_unaccent', 'relleno OTHER Area de soporte'), 'B')
                       || setweight(to_tsvector('es_unaccent', body.text), 'C')
            FROM filler f
            CROSS JOIN generate_series(0, :chunksPerDocument - 1) AS c(i)
            CROSS JOIN LATERAL (
                SELECT repeat('Registro rutinario de mantenimiento programado sobre el entorno interno '
                              || f.title || ' seccion ' || c.i || '. ', 4) AS text
            ) AS body
            """;

    private final JdbcClient jdbcClient;

    public SearchCorpus(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Chunks are removed with their document by the cascade on the foreign key. */
    public void clear() {
        jdbcClient.sql("DELETE FROM documents").update();
    }

    public UUID indexed(String title, String author, DocumentCategory category, List<String> tags, String... chunks) {
        return store(DocumentStatus.INDEXED, null, title, author, category, tags, chunks);
    }

    /** A document the worker has not finished with, or one it gave up on. */
    public UUID inStatus(DocumentStatus status, String title, String... chunks) {
        DocumentErrorCode errorCode = status == DocumentStatus.FAILED ? DocumentErrorCode.PROCESSING_FAILED : null;
        return store(status, errorCode, title, "Equipo de plataforma", DocumentCategory.OTHER, List.of(), chunks);
    }

    public void fill(int documents, int chunksPerDocument) {
        jdbcClient
                .sql(INSERT_FILLER)
                .param("status", DocumentStatus.INDEXED.wireValue())
                .param("documents", documents)
                .param("chunksPerDocument", chunksPerDocument)
                .update();
        analyze();
    }

    /** The planner only prefers the index once it has statistics that say the match is rare. */
    public void analyze() {
        jdbcClient.sql("ANALYZE documents").update();
        jdbcClient.sql("ANALYZE document_chunks").update();
    }

    private UUID store(
            DocumentStatus status,
            DocumentErrorCode errorCode,
            String title,
            String author,
            DocumentCategory category,
            List<String> tags,
            String... chunks) {

        UUID id = UUID.randomUUID();
        jdbcClient
                .sql(INSERT_DOCUMENT)
                .param("id", id)
                .param("title", title)
                .param("author", author)
                .param("category", category.name())
                .param("tags", textArray(tags))
                .param("storageKey", id + ".md")
                .param("sha256", DocumentFixtures.randomChecksum())
                .param("status", status.wireValue())
                .param("errorCode", errorCode == null ? null : errorCode.name())
                .param("chunkCount", chunks.length)
                .param("indexedAt", status == DocumentStatus.INDEXED ? OffsetDateTime.now() : null)
                .update();

        String metadata = String.join(" ", tags) + " " + category.name() + " " + author;
        for (int index = 0; index < chunks.length; index++) {
            jdbcClient
                    .sql(INSERT_CHUNK)
                    .param("documentId", id)
                    .param("chunkIndex", index)
                    .param("page", index + 1)
                    .param("heading", "Seccion " + index)
                    .param("content", chunks[index])
                    .param("title", title)
                    .param("metadata", metadata)
                    .update();
        }
        return id;
    }

    /** Bound on the statement's own connection, as a real PostgreSQL text[]. */
    private static SqlTypeValue textArray(List<String> values) {
        String[] tags = values.toArray(String[]::new);
        return (statement, index, sqlType, typeName) ->
                statement.setArray(index, statement.getConnection().createArrayOf("text", tags));
    }
}
