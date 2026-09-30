package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractedDocument;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractedUnit;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractionFailedException;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.UploadFileType;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.support.PdfFixtures;
import io.github.nicolassanchez1.technicaltestdavivienda.support.PdfFixtures.Page;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfTextExtractorTest {

    @TempDir
    Path directory;

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    private Path pdf(String name, Page... pages) {
        return PdfFixtures.write(directory.resolve(name), pages);
    }

    @Test
    void readsTheSupportedFormatFromTheTypeItself() {
        assertThat(extractor.supportedType()).isEqualTo(UploadFileType.PDF);
    }

    @Test
    void emitsOneUnitPerPageCarryingItsPageNumber() {
        Path file = pdf(
                "manual.pdf",
                Page.ofText("Introducción al manual"),
                Page.ofText("Requisitos del sistema"),
                Page.ofText("Procedimiento de instalación"));

        ExtractedDocument extracted = extractor.extract(file);

        assertThat(extracted.pageCount()).isEqualTo(3);
        assertThat(extracted.units()).extracting(ExtractedUnit::page).containsExactly(1, 2, 3);
        assertThat(extracted.units()).extracting(ExtractedUnit::heading).containsOnlyNulls();
        assertThat(extracted.units().get(0).text()).contains("Introducción al manual");
        assertThat(extracted.units().get(1).text()).contains("Requisitos del sistema");
        assertThat(extracted.units().get(2).text()).contains("Procedimiento de instalación");
    }

    @Test
    void keepsTheNumbersOfTheOtherPagesWhenOneCarriesNoText() {
        Path file = pdf("mixto.pdf", Page.ofText("Primera página"), Page.imageOnly(), Page.ofText("Tercera página"));

        ExtractedDocument extracted = extractor.extract(file);

        assertThat(extracted.pageCount()).isEqualTo(3);
        assertThat(extracted.units()).extracting(ExtractedUnit::page).containsExactly(1, 3);
    }

    @Test
    void failsWithNoTextLayerWhenEveryPageIsAnImage() {
        Path file = pdf("escaneado.pdf", Page.imageOnly(), Page.imageOnly());

        assertThatExceptionOfType(ExtractionFailedException.class)
                .isThrownBy(() -> extractor.extract(file))
                .extracting(ExtractionFailedException::errorCode)
                .isEqualTo(DocumentErrorCode.PDF_NO_TEXT_LAYER);
    }

    @Test
    void failsWithCorruptFileWhenTheBytesAreNotAPdf() throws IOException {
        Path file = directory.resolve("no-es-un-pdf.pdf");
        Files.write(file, "%PDF-1.7 y a partir de aquí sólo texto".getBytes(StandardCharsets.UTF_8));

        assertThatExceptionOfType(ExtractionFailedException.class)
                .isThrownBy(() -> extractor.extract(file))
                .extracting(ExtractionFailedException::errorCode)
                .isEqualTo(DocumentErrorCode.CORRUPT_FILE);
    }

    @Test
    void failsWithCorruptFileWhenThePdfIsTruncated() throws IOException {
        byte[] complete = Files.readAllBytes(pdf("completo.pdf", Page.ofText("Contenido completo")));
        Path truncated = directory.resolve("truncado.pdf");
        Files.write(truncated, Arrays.copyOf(complete, complete.length / 2));

        assertThatExceptionOfType(ExtractionFailedException.class)
                .isThrownBy(() -> extractor.extract(truncated))
                .extracting(ExtractionFailedException::errorCode)
                .isEqualTo(DocumentErrorCode.CORRUPT_FILE);
    }
}
