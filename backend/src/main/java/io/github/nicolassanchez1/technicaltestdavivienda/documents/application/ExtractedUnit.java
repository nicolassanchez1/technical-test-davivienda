package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

/**
 * One ordered piece of text an extractor read out of a file, before any chunking decision is taken.
 * At most one of {@code page} and {@code heading} is set: a PDF unit knows the page it was found on,
 * a Markdown unit knows the section it belongs to, and plain text knows neither.
 *
 * @param text never blank, because an extractor drops a unit that carries no text instead of
 *     emitting it
 * @param page the 1-based page the text was found on, or null for a format without pages
 * @param heading the nearest enclosing heading, or null when the text sits under none
 */
public record ExtractedUnit(String text, Integer page, String heading) {

    public ExtractedUnit {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("An extracted unit must carry text.");
        }
        if (page != null && page < 1) {
            throw new IllegalArgumentException("Page numbers start at 1, and this one is " + page + ".");
        }
        heading = heading == null || heading.isBlank() ? null : heading.strip();
    }

    public static ExtractedUnit onPage(int page, String text) {
        return new ExtractedUnit(text, page, null);
    }

    /** The heading may be null: Markdown text before the first heading sits under none. */
    public static ExtractedUnit underHeading(String heading, String text) {
        return new ExtractedUnit(text, null, heading);
    }

    public static ExtractedUnit of(String text) {
        return new ExtractedUnit(text, null, null);
    }
}
