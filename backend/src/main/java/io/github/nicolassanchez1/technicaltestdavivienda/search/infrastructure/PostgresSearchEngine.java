package io.github.nicolassanchez1.technicaltestdavivienda.search.infrastructure;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentCategory;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.github.nicolassanchez1.technicaltestdavivienda.search.application.SearchCriteria;
import io.github.nicolassanchez1.technicaltestdavivienda.search.application.SearchEngine;
import io.github.nicolassanchez1.technicaltestdavivienda.search.application.SearchFilters;
import io.github.nicolassanchez1.technicaltestdavivienda.search.application.SearchHit;
import io.github.nicolassanchez1.technicaltestdavivienda.search.application.SearchResults;
import io.github.nicolassanchez1.technicaltestdavivienda.shared.config.AppProperties;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL full-text search over the weighted vector the indexer writes.
 *
 * <p>The statement is one round trip built in three steps. {@code best} reduces the matching chunks
 * to the single highest ranked chunk of each document and is where the metadata filters are
 * applied, by joining {@code documents} there rather than afterwards: narrowing early is what stops
 * the ranking from being computed for documents that were going to be discarded. {@code page} adds
 * {@code count(*) OVER ()} so the total and the slice cost one query instead of two. Only then does
 * the outer select call {@code ts_headline}, over the handful of rows actually returned, because
 * {@code ts_headline} re-parses every character it is handed and running it over all matches is the
 * one change that reliably breaks the latency budget.
 */
@Repository
public class PostgresSearchEngine implements SearchEngine {

    /** The frontend renders these as highlight nodes, which is why no HTML is produced here. */
    public static final String HIGHLIGHT_START = "⟦";

    public static final String HIGHLIGHT_STOP = "⟧";

    private static final String SENTINELS = "StartSel=" + HIGHLIGHT_START + ", StopSel=" + HIGHLIGHT_STOP;
    private static final String SNIPPET_OPTIONS = SENTINELS + ", MaxFragments=2, MaxWords=25, MinWords=10";
    private static final String TITLE_OPTIONS = SENTINELS + ", HighlightAll=true";

    /** {@code SET} takes no bind parameter, so the timeout is applied transaction locally instead. */
    private static final String APPLY_STATEMENT_TIMEOUT = "SELECT set_config('statement_timeout', :timeout, true)";

    /**
     * Zero nodes is what the parser reports for a query that held nothing it could index: an empty
     * string, punctuation only, or only words the configuration drops.
     */
    private static final String COUNT_QUERY_NODES = "SELECT numnode(websearch_to_tsquery('es_unaccent', :query))";

    private static final String SEARCH_TEMPLATE =
            """
            WITH q AS (
                SELECT websearch_to_tsquery('es_unaccent', :query) AS query
            ),
            best AS (
                SELECT DISTINCT ON (c.document_id)
                       c.document_id,
                       c.id AS chunk_id,
                       ts_rank_cd(c.search_vector, q.query) AS rank
                FROM document_chunks c
                CROSS JOIN q
                JOIN documents d ON d.id = c.document_id
                WHERE c.search_vector @@ q.query
                  AND d.status = :indexedStatus%s
                ORDER BY c.document_id, rank DESC, c.chunk_index
            ),
            matches AS (
                SELECT count(*) AS total FROM best
            ),
            page AS (
                SELECT best.*
                FROM best
                ORDER BY rank DESC, document_id
                LIMIT :limit OFFSET :offset
            )
            SELECT d.id, d.title, d.author, d.category, d.tags, d.version, d.indexed_at,
                   p.rank, m.total, ch.chunk_index, ch.page, ch.heading,
                   ts_headline('es_unaccent', ch.content, q.query, '%s') AS snippet,
                   ts_headline('es_unaccent', d.title, q.query, '%s') AS title_highlight
            FROM matches m
            LEFT JOIN page p           ON true
            LEFT JOIN documents d      ON d.id = p.document_id
            LEFT JOIN document_chunks ch ON ch.id = p.chunk_id
            CROSS JOIN q
            ORDER BY p.rank DESC, d.id
            """;

    /**
     * The total is counted over every match and carried by the statement itself, so a page past the
     * last one still reports how many documents matched. That row has no document joined to it, so
     * the identifier is what separates a real hit from the count-only row.
     */
    private static final ResultSetExtractor<SearchResults> RESULTS = resultSet -> {
        List<SearchHit> hits = new ArrayList<>();
        long total = 0;
        while (resultSet.next()) {
            total = resultSet.getLong("total");
            if (resultSet.getObject("id") != null) {
                hits.add(hitOf(resultSet));
            }
        }
        return new SearchResults(hits, total);
    };

    private final JdbcClient jdbcClient;
    private final String statementTimeout;

    public PostgresSearchEngine(JdbcClient jdbcClient, AppProperties properties) {
        this.jdbcClient = jdbcClient;
        this.statementTimeout = String.valueOf(properties.searchTimeoutMs());
    }

    @Override
    public boolean hasSearchableTerms(String query) {
        Integer nodes = jdbcClient
                .sql(COUNT_QUERY_NODES)
                .param("query", query)
                .query(Integer.class)
                .single();
        return nodes != null && nodes > 0;
    }

    /**
     * The transaction exists for the timeout: {@code set_config} with a local scope only holds
     * until the transaction ends, so the ceiling applies to this search and never leaks onto the
     * next statement that happens to reuse the pooled connection. A cancelled statement arrives as
     * SQLSTATE 57014 and the exception handler answers 503.
     */
    @Override
    @Transactional(readOnly = true)
    public SearchResults search(SearchCriteria criteria) {
        applyStatementTimeout();

        JdbcClient.StatementSpec statement = jdbcClient
                .sql(searchStatement(criteria.filters()))
                .param("query", criteria.query())
                .param("indexedStatus", DocumentStatus.INDEXED.wireValue())
                .param("limit", criteria.limit())
                .param("offset", criteria.offset());

        SearchResults results = bindFilters(statement, criteria.filters()).query(RESULTS);
        return results == null ? SearchResults.empty() : results;
    }

    /** The plan of a real search, as the benchmark and the tests read it back. */
    @Transactional(readOnly = true)
    public String explainSearch(SearchCriteria criteria) {
        JdbcClient.StatementSpec statement = jdbcClient
                .sql("EXPLAIN (ANALYZE, BUFFERS) " + searchStatement(criteria.filters()))
                .param("query", criteria.query())
                .param("indexedStatus", DocumentStatus.INDEXED.wireValue())
                .param("limit", criteria.limit())
                .param("offset", criteria.offset());

        return String.join(
                "\n",
                bindFilters(statement, criteria.filters()).query(String.class).list());
    }

    private void applyStatementTimeout() {
        jdbcClient
                .sql(APPLY_STATEMENT_TIMEOUT)
                .param("timeout", statementTimeout)
                .query(String.class)
                .single();
    }

    /**
     * Only the predicates the caller asked for reach the statement. A filter written once as
     * {@code (CAST(:author AS text) IS NULL OR d.author = :author)} would be carried by every
     * search and cost the planner the equality it could otherwise rely on.
     */
    static String searchStatement(SearchFilters filters) {
        StringBuilder predicates = new StringBuilder();
        if (filters.byCategory()) {
            predicates.append("\n      AND d.category = :category");
        }
        if (filters.byAuthor()) {
            predicates.append("\n      AND d.author = :author");
        }
        if (filters.byTags()) {
            predicates.append("\n      AND d.tags @> :tags");
        }
        return SEARCH_TEMPLATE.formatted(predicates, SNIPPET_OPTIONS, TITLE_OPTIONS);
    }

    private static JdbcClient.StatementSpec bindFilters(JdbcClient.StatementSpec statement, SearchFilters filters) {
        JdbcClient.StatementSpec bound = statement;
        if (filters.byCategory()) {
            bound = bound.param("category", filters.category().name());
        }
        if (filters.byAuthor()) {
            bound = bound.param("author", filters.author());
        }
        if (filters.byTags()) {
            bound = bound.param("tags", textArray(filters.tags()));
        }
        return bound;
    }

    private static SearchHit hitOf(ResultSet row) throws SQLException {
        return new SearchHit(
                row.getObject("id", UUID.class),
                row.getString("title"),
                row.getString("title_highlight"),
                row.getString("author"),
                DocumentCategory.valueOf(row.getString("category")),
                tagsOf(row),
                row.getString("version"),
                instantOf(row),
                row.getInt("chunk_index"),
                row.getObject("page", Integer.class),
                row.getString("heading"),
                row.getString("snippet"),
                row.getDouble("rank"));
    }

    /** Binds the tags on the same connection the statement runs on, as a real PostgreSQL text[]. */
    private static SqlTypeValue textArray(List<String> values) {
        String[] tags = values.toArray(String[]::new);
        return (statement, index, sqlType, typeName) ->
                statement.setArray(index, statement.getConnection().createArrayOf("text", tags));
    }

    private static List<String> tagsOf(ResultSet row) throws SQLException {
        Array stored = row.getArray("tags");
        return stored == null ? List.of() : List.of((String[]) stored.getArray());
    }

    private static Instant instantOf(ResultSet row) throws SQLException {
        OffsetDateTime stored = row.getObject("indexed_at", OffsetDateTime.class);
        return stored == null ? null : stored.toInstant();
    }
}
