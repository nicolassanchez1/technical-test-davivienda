package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Cuts an extracted document into the chunks that get indexed, following the structure the format
 * already gives: a PDF page and a Markdown section are what a reader navigates, so each becomes one
 * chunk, while plain text has no structure and is packed up to a word ceiling at paragraph
 * boundaries.
 */
@Component
public class DocumentChunker {

    /** Roughly a handful of pages of prose: enough context for a snippet, small enough to rank. */
    public static final int MAX_CHUNK_WORDS = 1_500;

    /**
     * No chunk may pass this many characters, whatever its format: {@code ts_headline} re-parses
     * every character handed to it, a tsvector caps at 1 MB, and lexeme positions saturate at
     * 16,383, which would cost phrase search and highlighting inside an oversized chunk.
     */
    public static final int MAX_CHUNK_CHARACTERS = 20_000;

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n[ \\t]*\\n\\s*");

    /** @return the chunks in reading order, numbered from 0, never carrying blank content */
    public List<DocumentChunkDraft> chunk(ExtractedDocument extracted, UploadFileType fileType) {
        Objects.requireNonNull(extracted, "extracted");
        Objects.requireNonNull(fileType, "fileType");

        List<DocumentChunkDraft> chunks = new ArrayList<>(extracted.units().size());
        for (ExtractedUnit unit : extracted.units()) {
            for (String content : contentsOf(unit.text(), fileType)) {
                chunks.add(new DocumentChunkDraft(chunks.size(), unit.page(), unit.heading(), content));
            }
        }
        return List.copyOf(chunks);
    }

    private static List<String> contentsOf(String text, UploadFileType fileType) {
        List<String> pieces =
                switch (fileType) {
                    // A page and a section are the unit the reader sees, so each one stays whole
                    // unless it does not fit a single chunk.
                    case PDF, MARKDOWN -> List.of(text);
                    case PLAIN_TEXT -> paragraphGroups(text);
                };
        return withinCharacterCap(pieces);
    }

    /**
     * Packs whole paragraphs until the next one would pass the word ceiling. A paragraph is only cut
     * in two when it passes the ceiling on its own, because a chunk that starts mid-sentence reads
     * badly both in the viewer and in a snippet.
     */
    private static List<String> paragraphGroups(String text) {
        List<String> groups = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentWords = 0;

        for (String paragraph : PARAGRAPH_BREAK.split(text)) {
            String candidate = paragraph.strip();
            if (candidate.isEmpty()) {
                continue;
            }
            int candidateWords = countWords(candidate);
            boolean doesNotFit = candidateWords > MAX_CHUNK_WORDS || currentWords + candidateWords > MAX_CHUNK_WORDS;
            if (doesNotFit && !current.isEmpty()) {
                groups.add(current.toString());
                current.setLength(0);
                currentWords = 0;
            }
            if (candidateWords > MAX_CHUNK_WORDS) {
                groups.addAll(byWordCeiling(candidate));
                continue;
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(candidate);
            currentWords += candidateWords;
        }
        if (!current.isEmpty()) {
            groups.add(current.toString());
        }
        return groups;
    }

    /** Cuts an oversized paragraph at the word boundary closest to the ceiling. */
    private static List<String> byWordCeiling(String paragraph) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        int words = 0;
        boolean insideWord = false;

        for (int index = 0; index < paragraph.length(); index++) {
            boolean whitespace = Character.isWhitespace(paragraph.charAt(index));
            if (!whitespace && !insideWord) {
                insideWord = true;
                words++;
            } else if (whitespace && insideWord) {
                insideWord = false;
                if (words >= MAX_CHUNK_WORDS) {
                    add(pieces, paragraph.substring(start, index));
                    start = index;
                    words = 0;
                }
            }
        }
        add(pieces, paragraph.substring(start));
        return pieces;
    }

    private static List<String> withinCharacterCap(List<String> pieces) {
        List<String> capped = new ArrayList<>(pieces.size());
        for (String piece : pieces) {
            int start = 0;
            while (start < piece.length()) {
                int end = breakPoint(piece, start);
                add(capped, piece.substring(start, end));
                start = end;
            }
        }
        return capped;
    }

    /**
     * Where to cut so that no chunk passes the character cap: the last line break inside the window,
     * then the last whitespace, and only failing both the cap itself, which is what an unbroken run
     * of characters leaves.
     */
    private static int breakPoint(String text, int start) {
        int cap = start + MAX_CHUNK_CHARACTERS;
        if (cap >= text.length()) {
            return text.length();
        }
        int floor = start + MAX_CHUNK_CHARACTERS / 2;
        for (int index = cap; index > floor; index--) {
            if (text.charAt(index - 1) == '\n') {
                return index;
            }
        }
        for (int index = cap; index > floor; index--) {
            if (Character.isWhitespace(text.charAt(index - 1))) {
                return index;
            }
        }
        return cap;
    }

    private static void add(List<String> pieces, String piece) {
        String content = piece.strip();
        if (!content.isEmpty()) {
            pieces.add(content);
        }
    }

    private static int countWords(String text) {
        int words = 0;
        boolean insideWord = false;
        for (int index = 0; index < text.length(); index++) {
            if (Character.isWhitespace(text.charAt(index))) {
                insideWord = false;
            } else if (!insideWord) {
                insideWord = true;
                words++;
            }
        }
        return words;
    }
}
