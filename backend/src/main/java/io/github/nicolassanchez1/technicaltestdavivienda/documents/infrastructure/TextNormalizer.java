package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import java.util.regex.Pattern;

/**
 * Brings extracted text to the shape the rest of the pipeline expects. Every rule here exists
 * because a real file breaks something downstream: a PDF glues words together with a no-break space,
 * a page break arrives as a form feed, and a stray NUL makes PostgreSQL reject the whole insert.
 */
final class TextNormalizer {

    private static final Pattern LINE_BREAK = Pattern.compile("\\r\\n|[\\r\\u0085\\u2028\\u2029]");
    private static final Pattern PAGE_BREAK = Pattern.compile("\\f+");
    /** Control characters PostgreSQL will not store, and the invisible formatting ones nobody searches for. */
    private static final Pattern UNPRINTABLE = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]|\\p{Cf}");

    private static final Pattern NO_BREAK_SPACE = Pattern.compile("[\\u00a0\\u2007\\u202f]");
    private static final Pattern TRAILING_SPACES = Pattern.compile("[ \\t]+(?=\\n)");
    private static final Pattern BLANK_RUN = Pattern.compile("\\n{3,}");

    private TextNormalizer() {}

    static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String text = LINE_BREAK.matcher(raw).replaceAll("\n");
        text = PAGE_BREAK.matcher(text).replaceAll("\n\n");
        text = UNPRINTABLE.matcher(text).replaceAll("");
        text = NO_BREAK_SPACE.matcher(text).replaceAll(" ");
        text = TRAILING_SPACES.matcher(text).replaceAll("");
        // One blank line is a paragraph boundary the chunker reads; more of them carry nothing.
        return BLANK_RUN.matcher(text).replaceAll("\n\n").strip();
    }
}
