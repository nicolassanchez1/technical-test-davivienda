package io.github.nicolassanchez1.technicaltestdavivienda.documents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentTest {

    private static Document documentWithTags(List<String> tags) {
        Instant now = Instant.parse("2026-09-29T10:15:30Z");
        return new Document(
                UUID.randomUUID(),
                "Guia de arquitectura",
                "Equipo de plataforma",
                DocumentCategory.ARCHITECTURE_GUIDE,
                tags,
                "1.0",
                "guia.md",
                "text/markdown",
                2048,
                "1f0d8d0e-6b1a-4f2c-9a4d-0c7e9f5b1a23.md",
                "a".repeat(64),
                DocumentStatus.PROCESSING,
                null,
                null,
                null,
                null,
                null,
                now,
                now,
                null);
    }

    @Test
    void treatsMissingTagsAsNoTags() {
        assertThat(documentWithTags(null).tags()).isEmpty();
    }

    @Test
    void doesNotShareTheListItWasBuiltFrom() {
        List<String> mutable = new ArrayList<>(List.of("java"));

        Document document = documentWithTags(mutable);
        mutable.add("spring");

        assertThat(document.tags()).containsExactly("java");
    }

    @Test
    void exposesTagsAsAnUnmodifiableList() {
        Document document = documentWithTags(List.of("java"));

        assertThatThrownBy(() -> document.tags().add("spring")).isInstanceOf(UnsupportedOperationException.class);
    }
}
