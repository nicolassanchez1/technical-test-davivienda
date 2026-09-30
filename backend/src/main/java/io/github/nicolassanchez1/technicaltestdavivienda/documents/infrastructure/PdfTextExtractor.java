package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractedDocument;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractedUnit;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractionFailedException;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.UploadFileType;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Reads a PDF page by page, so a hit can be reported as "page 7" and the viewer can open there.
 *
 * <p>A page whose text layer is empty contributes no unit while still counting towards the page
 * count, and a file where that is true of every page is a scan: there is nothing to index and no
 * amount of retrying will change that, which the spec expects to surface as {@code ERROR} with
 * {@code PDF_NO_TEXT_LAYER}.
 */
@Component
public class PdfTextExtractor implements FormatTextExtractor {

    @Override
    public UploadFileType supportedType() {
        return UploadFileType.PDF;
    }

    @Override
    public ExtractedDocument extract(Path file) {
        try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
            int pageCount = pdf.getNumberOfPages();
            List<ExtractedUnit> pages = pagesOf(pdf, pageCount);
            if (pages.isEmpty()) {
                throw new ExtractionFailedException(
                        DocumentErrorCode.PDF_NO_TEXT_LAYER,
                        "None of the %d page(s) of %s carries a text layer.".formatted(pageCount, file.getFileName()));
            }
            return ExtractedDocument.paged(pages, pageCount);
        } catch (IOException cause) {
            throw new ExtractionFailedException(
                    DocumentErrorCode.CORRUPT_FILE, "The PDF could not be parsed: " + file.getFileName(), cause);
        }
    }

    private static List<ExtractedUnit> pagesOf(PDDocument pdf, int pageCount) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        // Reading order rather than drawing order, so a two-column page does not interleave.
        stripper.setSortByPosition(true);
        stripper.setLineSeparator("\n");
        stripper.setPageEnd("\n");

        List<ExtractedUnit> pages = new ArrayList<>(pageCount);
        for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            stripper.setStartPage(pageNumber);
            stripper.setEndPage(pageNumber);
            String text = TextNormalizer.normalize(stripper.getText(pdf));
            if (!text.isBlank()) {
                pages.add(ExtractedUnit.onPage(pageNumber, text));
            }
        }
        return pages;
    }
}
