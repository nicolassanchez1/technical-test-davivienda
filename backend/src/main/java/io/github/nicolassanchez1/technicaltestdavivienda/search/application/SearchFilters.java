package io.github.nicolassanchez1.technicaltestdavivienda.search.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentCategory;
import java.util.List;

/**
 * The metadata a reader narrows a search by. Every filter is an exact match on an indexed column:
 * equality for the category and the author, array containment for the tags. None of them is ever a
 * text scan, which is what keeps the cost of narrowing a search independent of the corpus size.
 */
public record SearchFilters(DocumentCategory category, String author, List<String> tags) {

    public SearchFilters {
        author = author == null || author.isBlank() ? null : author.trim();
        tags = tags == null
                ? List.of()
                : tags.stream().filter(tag -> !tag.isBlank()).map(String::trim).toList();
    }

    public static SearchFilters none() {
        return new SearchFilters(null, null, List.of());
    }

    public boolean byCategory() {
        return category != null;
    }

    public boolean byAuthor() {
        return author != null;
    }

    public boolean byTags() {
        return !tags.isEmpty();
    }
}
