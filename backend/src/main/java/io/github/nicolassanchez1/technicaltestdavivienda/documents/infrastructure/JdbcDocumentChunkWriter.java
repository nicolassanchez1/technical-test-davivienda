package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentChunkDraft;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentChunkWriter;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Writes the chunks of one document together with the vector they are searched by.
 *
 * <p>The weighting is the contract the search path ranks on: the title weighs most, the metadata
 * next and the chunk's own text last, and every field goes through {@code es_unaccent} so that
 * "especificacion" finds "Especificación". Computing it here rather than in Java is what keeps the
 * stored vector and the query built by {@code websearch_to_tsquery} parsed by the same
 * configuration.
 *
 * <p>All the chunks of a document are inserted by a single statement. The rows arrive as parallel
 * arrays that {@code unnest} expands, so indexing a 400 page PDF is one round trip and one plan
 * rather than 400 of each.
 */
@Repository
public class JdbcDocumentChunkWriter implements DocumentChunkWriter {

    private static final String DELETE_BY_DOCUMENT = "DELETE FROM document_chunks WHERE document_id = :documentId";

    private static final String INSERT_CHUNKS =
            """
            INSERT INTO document_chunks (document_id, chunk_index, page, heading, content, search_vector)
            SELECT :documentId,
                   chunk.chunk_index,
                   chunk.page,
                   chunk.heading,
                   chunk.content,
                   setweight(to_tsvector('es_unaccent', coalesce(:title, '')), 'A')
                       || setweight(to_tsvector('es_unaccent', coalesce(:metadata, '')), 'B')
                       || setweight(to_tsvector('es_unaccent', coalesce(chunk.content, '')), 'C')
            FROM unnest(:chunkIndexes, :pages, :headings, :contents)
                 AS chunk(chunk_index, page, heading, content)
            """;

    private final JdbcClient jdbcClient;

    public JdbcDocumentChunkWriter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public int deleteByDocument(UUID documentId) {
        return jdbcClient
                .sql(DELETE_BY_DOCUMENT)
                .param("documentId", documentId)
                .update();
    }

    @Override
    public void insertAll(Document document, List<DocumentChunkDraft> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        List<Integer> chunkIndexes = new ArrayList<>(chunks.size());
        List<Integer> pages = new ArrayList<>(chunks.size());
        List<String> headings = new ArrayList<>(chunks.size());
        List<String> contents = new ArrayList<>(chunks.size());
        for (DocumentChunkDraft chunk : chunks) {
            chunkIndexes.add(chunk.chunkIndex());
            pages.add(chunk.page());
            headings.add(chunk.heading());
            contents.add(chunk.content());
        }

        jdbcClient
                .sql(INSERT_CHUNKS)
                .param("documentId", document.id())
                .param("title", document.title())
                .param("metadata", searchableMetadata(document))
                .param("chunkIndexes", array("integer", chunkIndexes))
                .param("pages", array("integer", pages))
                .param("headings", array("text", headings))
                .param("contents", array("text", contents))
                .update();
    }

    /** Everything a reader may search a document by without knowing a word of its body. */
    private static String searchableMetadata(Document document) {
        List<String> parts = new ArrayList<>(document.tags());
        parts.add(document.category().name());
        parts.add(document.author());
        return String.join(" ", parts);
    }

    /** Binds a real PostgreSQL array on the connection the statement runs on, null elements included. */
    private static SqlTypeValue array(String elementType, List<?> values) {
        Object[] elements = values.toArray();
        return (statement, index, sqlType, typeName) ->
                statement.setArray(index, statement.getConnection().createArrayOf(elementType, elements));
    }
}
