package io.github.nicolassanchez1.technicaltestdavivienda.documents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DocumentStatusTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void keepsTheSpanishWireValuesTheSpecFixes() {
        assertThat(DocumentStatus.PROCESSING.wireValue()).isEqualTo("PROCESANDO");
        assertThat(DocumentStatus.INDEXED.wireValue()).isEqualTo("INDEXADO");
        assertThat(DocumentStatus.FAILED.wireValue()).isEqualTo("ERROR");
    }

    @ParameterizedTest
    @EnumSource(DocumentStatus.class)
    void serializesAndReadsBackEveryStatus(DocumentStatus status) throws Exception {
        String json = objectMapper.writeValueAsString(status);

        assertThat(json).isEqualTo("\"" + status.wireValue() + "\"");
        assertThat(objectMapper.readValue(json, DocumentStatus.class)).isEqualTo(status);
    }

    @Test
    void rejectsAnUnknownWireValue() {
        assertThatThrownBy(() -> DocumentStatus.fromWireValue("PENDIENTE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PENDIENTE");
    }

    @Test
    void treatsOnlyIndexedAndFailedAsTerminal() {
        assertThat(DocumentStatus.INDEXED.isTerminal()).isTrue();
        assertThat(DocumentStatus.FAILED.isTerminal()).isTrue();
        assertThat(DocumentStatus.PROCESSING.isTerminal()).isFalse();
    }
}
