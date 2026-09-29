package io.github.nicolassanchez1.technicaltestdavivienda.documents.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DocumentCategory {
    MANUAL,
    SPECIFICATION,
    ARCHITECTURE_GUIDE,
    OTHER;

    @JsonValue
    public String wireValue() {
        return name();
    }
}
