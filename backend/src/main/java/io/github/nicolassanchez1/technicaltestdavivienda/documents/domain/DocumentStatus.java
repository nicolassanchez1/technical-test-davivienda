package io.github.nicolassanchez1.technicaltestdavivienda.documents.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Lifecycle of a document. The constant names are English; the wire values are the Spanish
 * strings the spec fixes as API contract, and they are what the database stores.
 */
public enum DocumentStatus {
    PROCESSING("PROCESANDO"),
    INDEXED("INDEXADO"),
    FAILED("ERROR");

    private final String wireValue;

    DocumentStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static DocumentStatus fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown document status: " + value));
    }

    /** A document leaves PROCESSING exactly once; INDEXED and FAILED are terminal. */
    public boolean isTerminal() {
        return this == INDEXED || this == FAILED;
    }
}
