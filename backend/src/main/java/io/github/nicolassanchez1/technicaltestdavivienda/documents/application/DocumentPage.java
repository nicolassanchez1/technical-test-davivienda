package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import java.util.List;

/** One slice of the document list together with how many documents the filter matches overall. */
public record DocumentPage(List<Document> items, long total) {

    public DocumentPage {
        items = List.copyOf(items);
    }
}
