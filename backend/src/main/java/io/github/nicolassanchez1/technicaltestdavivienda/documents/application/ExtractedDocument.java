package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import java.util.List;

/**
 * What an extractor read out of a stored file: the text units in reading order, plus the two things
 * only the original format knows.
 *
 * @param units at least one, in the order they appear in the document
 * @param pageCount how many pages the file holds, or null for a format without pages. It counts the
 *     pages of the file and not the units: a page without a text layer is counted here and
 *     contributes no unit.
 */
public record ExtractedDocument(List<ExtractedUnit> units, Integer pageCount) {

    public ExtractedDocument {
        units = List.copyOf(units);
        if (units.isEmpty()) {
            throw new IllegalArgumentException("An extracted document must carry at least one unit.");
        }
        if (pageCount != null && pageCount < 1) {
            throw new IllegalArgumentException("A paged document holds at least one page, not " + pageCount + ".");
        }
    }

    public static ExtractedDocument paged(List<ExtractedUnit> units, int pageCount) {
        return new ExtractedDocument(units, pageCount);
    }

    public static ExtractedDocument of(List<ExtractedUnit> units) {
        return new ExtractedDocument(units, null);
    }
}
