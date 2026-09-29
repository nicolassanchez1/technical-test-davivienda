package io.github.nicolassanchez1.technicaltestdavivienda;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicolassanchez1.technicaltestdavivienda.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class DatabaseSchemaIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void appliesTheFlywayBaseline() {
        Integer applied = jdbcClient
                .sql("SELECT count(*) FROM flyway_schema_history WHERE success")
                .query(Integer.class)
                .single();

        assertThat(applied).isPositive();
    }

    @Test
    void installsTheAccentInsensitiveSearchConfiguration() {
        Boolean exists = jdbcClient
                .sql("SELECT exists(SELECT 1 FROM pg_ts_config WHERE cfgname = 'es_unaccent')")
                .query(Boolean.class)
                .single();

        assertThat(exists).isTrue();
    }

    @Test
    void stripsAccentsWhenIndexingAndQuerying() {
        Boolean matches = jdbcClient
                .sql(
                        """
                        SELECT to_tsvector('es_unaccent', :indexed)
                               @@ websearch_to_tsquery('es_unaccent', :query)
                        """)
                .param("indexed", "Especificación técnica de arquitectura")
                .param("query", "especificacion tecnica")
                .query(Boolean.class)
                .single();

        assertThat(matches).isTrue();
    }
}
