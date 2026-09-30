package io.github.nicolassanchez1.technicaltestdavivienda.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

/**
 * Builds the PDFs the extractor tests read. They are written at test time instead of being committed
 * as binaries, so what each page holds is visible in the test that asks for it.
 */
public final class PdfFixtures {

    private static final float MARGIN = 72f;
    private static final float TOP = 720f;
    private static final float FONT_SIZE = 12f;
    private static final float LEADING = 16f;

    private PdfFixtures() {}

    /** A page of a generated PDF: either text in a standard font, or a drawn image and no text. */
    public record Page(String text) {

        public static Page ofText(String text) {
            return new Page(text);
        }

        /** What a scanned document looks to an extractor: pixels, no text layer. */
        public static Page imageOnly() {
            return new Page(null);
        }
    }

    public static Path write(Path file, Page... pages) {
        return write(file, List.of(pages));
    }

    public static Path writeTextPages(Path file, String... texts) {
        return write(file, Stream.of(texts).map(Page::ofText).toList());
    }

    public static Path write(Path file, List<Page> pages) {
        try (PDDocument document = new PDDocument()) {
            for (Page page : pages) {
                addPage(document, page);
            }
            document.save(file.toFile());
            return file;
        } catch (IOException cause) {
            throw new UncheckedIOException("Could not write the PDF fixture " + file, cause);
        }
    }

    private static void addPage(PDDocument document, Page page) throws IOException {
        PDPage pdPage = new PDPage(PDRectangle.A4);
        document.addPage(pdPage);
        try (PDPageContentStream content = new PDPageContentStream(document, pdPage)) {
            if (page.text() == null) {
                drawImage(document, content);
            } else {
                drawText(content, page.text());
            }
        }
    }

    private static void drawText(PDPageContentStream content, String text) throws IOException {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE);
        content.setLeading(LEADING);
        content.newLineAtOffset(MARGIN, TOP);
        for (String line : text.split("\n", -1)) {
            content.showText(line);
            content.newLine();
        }
        content.endText();
    }

    private static void drawImage(PDDocument document, PDPageContentStream content) throws IOException {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.DARK_GRAY);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        content.drawImage(LosslessFactory.createFromImage(document, image), MARGIN, 500f, 200f, 200f);
    }
}
