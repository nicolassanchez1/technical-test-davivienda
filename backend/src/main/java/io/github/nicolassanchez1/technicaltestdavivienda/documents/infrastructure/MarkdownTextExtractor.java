package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractedDocument;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractedUnit;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractionFailedException;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.UploadFileType;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

/**
 * Reads a Markdown file two ways, because its two consumers want different text. The viewer renders
 * the markup as it was written, so the source is kept verbatim; the index wants the prose with the
 * markup taken away and cut by heading, so every h1 to h3 section becomes one unit carrying it.
 */
@Component
public class MarkdownTextExtractor implements FormatTextExtractor {

    /** Deeper headings stay inside their section: below h3 a heading titles a detail, not a part. */
    private static final int SECTION_HEADING_DEPTH = 3;

    private final Parser parser = Parser.builder().build();
    private final TextFileDecoder decoder;

    public MarkdownTextExtractor(TextFileDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public UploadFileType supportedType() {
        return UploadFileType.MARKDOWN;
    }

    @Override
    public ExtractedDocument extract(Path file) {
        String markdown = decoder.decode(file);
        List<ExtractedUnit> sections = sectionsOf(parser.parse(markdown));
        if (sections.isEmpty()) {
            throw new ExtractionFailedException(
                    DocumentErrorCode.EMPTY_CONTENT, "The Markdown file holds no text to index: " + file.getFileName());
        }
        return ExtractedDocument.of(sections);
    }

    private static List<ExtractedUnit> sectionsOf(Node document) {
        List<ExtractedUnit> sections = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        String heading = null;

        for (Node block = document.getFirstChild(); block != null; block = block.getNext()) {
            if (startsASection(block)) {
                addSection(sections, heading, body);
                heading = MarkdownPlainText.of(block);
                body.setLength(0);
                // The heading is text of its own section too: a search may match it, and the viewer
                // shows the section starting at it.
                body.append(heading);
                continue;
            }
            append(body, MarkdownPlainText.of(block));
        }
        addSection(sections, heading, body);
        return sections;
    }

    private static boolean startsASection(Node block) {
        return block instanceof Heading heading && heading.getLevel() <= SECTION_HEADING_DEPTH;
    }

    private static void append(StringBuilder body, String blockText) {
        if (blockText.isEmpty()) {
            return;
        }
        if (!body.isEmpty()) {
            body.append("\n\n");
        }
        body.append(blockText);
    }

    /** Text before the first heading becomes a section without one; an empty section is dropped. */
    private static void addSection(List<ExtractedUnit> sections, String heading, StringBuilder body) {
        String text = TextNormalizer.normalize(body.toString());
        if (!text.isBlank()) {
            sections.add(ExtractedUnit.underHeading(heading, text));
        }
    }
}
