package io.github.nicolassanchez1.technicaltestdavivienda.documents.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Metadata the uploader supplies for one file. Tags are canonicalised on construction, because
 * "Java", " java " and "JAVA" must not become three different facets for the same concept.
 */
public record DocumentMetadata(
        @NotBlank @Size(max = DocumentMetadata.TITLE_MAX_LENGTH) String title,
        @NotBlank @Size(max = DocumentMetadata.AUTHOR_MAX_LENGTH) String author,
        @NotNull DocumentCategory category,
        @Size(max = DocumentMetadata.TAGS_MAX_COUNT) List<@NotBlank @Size(max = DocumentMetadata.TAG_MAX_LENGTH) String> tags,
        @NotBlank @Size(max = DocumentMetadata.VERSION_MAX_LENGTH) String version) {

    public static final int TITLE_MAX_LENGTH = 300;
    public static final int AUTHOR_MAX_LENGTH = 200;
    public static final int VERSION_MAX_LENGTH = 50;
    public static final int TAG_MAX_LENGTH = 50;
    public static final int TAGS_MAX_COUNT = 20;

    public DocumentMetadata {
        tags = canonicalTags(tags);
    }

    private static List<String> canonicalTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        Set<String> canonical = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String stripped = tag.strip().toLowerCase(Locale.ROOT);
            if (!stripped.isEmpty()) {
                canonical.add(stripped);
            }
        }
        return List.copyOf(canonical);
    }
}
