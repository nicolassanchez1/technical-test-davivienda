package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractedDocument;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractionFailedException;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.TextExtractor;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.UploadFileType;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.support.Fixtures;
import io.github.nicolassanchez1.technicaltestdavivienda.support.PdfFixtures;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DispatchingTextExtractorTest {

    @TempDir
    Path directory;

    private final TextFileDecoder decoder = new TextFileDecoder();
    private final PlainTextExtractor plainText = new PlainTextExtractor(decoder);
    private final TextExtractor extractor = new DispatchingTextExtractor(
            List.of(plainText, new MarkdownTextExtractor(decoder), new PdfTextExtractor()));

    @Test
    void readsAPlainTextFileAsOneUnitWithoutPages() {
        ExtractedDocument extracted =
                extractor.extract(Fixtures.path("plain-text-utf8.txt"), UploadFileType.PLAIN_TEXT);

        assertThat(extracted.units()).hasSize(1);
        assertThat(extracted.pageCount()).isNull();
    }

    @Test
    void readsAMarkdownFileIntoSectionsThatKeepTheirSource() {
        ExtractedDocument extracted = extractor.extract(Fixtures.path("markdown-sections.md"), UploadFileType.MARKDOWN);

        assertThat(extracted.units()).hasSizeGreaterThan(1);
    }

    @Test
    void readsAPdfIntoPagesThatKeepTheirCount() {
        Path file = PdfFixtures.writeTextPages(directory.resolve("manual.pdf"), "Primera página", "Segunda página");

        ExtractedDocument extracted = extractor.extract(file, UploadFileType.PDF);

        assertThat(extracted.pageCount()).isEqualTo(2);
        assertThat(extracted.units()).hasSize(2);
    }

    @Test
    void failsWithUnsupportedFormatWhenNoExtractorClaimsTheType() {
        TextExtractor onlyPlainText = new DispatchingTextExtractor(List.of(plainText));

        assertThatExceptionOfType(ExtractionFailedException.class)
                .isThrownBy(() -> onlyPlainText.extract(Fixtures.path("plain-text-utf8.txt"), UploadFileType.PDF))
                .extracting(ExtractionFailedException::errorCode)
                .isEqualTo(DocumentErrorCode.UNSUPPORTED_FORMAT);
    }

    /** A file the shared volume has not published yet is not a broken document, so it stays retryable. */
    @Test
    void reportsAMissingFileAsAnIoFailureRatherThanAVerdictOnTheContent() {
        Path missing = directory.resolve("nunca-se-guardo.txt");

        assertThatExceptionOfType(UncheckedIOException.class)
                .isThrownBy(() -> extractor.extract(missing, UploadFileType.PLAIN_TEXT))
                .withMessageContaining("cannot be read");
    }

    @Test
    void refusesTwoExtractorsClaimingTheSameFormat() {
        List<FormatTextExtractor> ambiguous = List.of(plainText, new PlainTextExtractor(decoder));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new DispatchingTextExtractor(ambiguous));
    }
}
