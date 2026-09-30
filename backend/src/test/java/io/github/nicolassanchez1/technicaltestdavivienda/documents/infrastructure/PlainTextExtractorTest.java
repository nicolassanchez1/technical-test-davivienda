package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractedDocument;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractionFailedException;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.UploadFileType;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.support.Fixtures;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlainTextExtractorTest {

    private static final String LATIN_1_FIXTURE = "plain-text-latin1.txt";
    private static final byte E_ACUTE_IN_LATIN_1 = (byte) 0xE9;
    private static final String REPLACEMENT_CHARACTER = "\uFFFD";

    @TempDir
    Path directory;

    private final PlainTextExtractor extractor = new PlainTextExtractor(new TextFileDecoder());

    private Path write(String name, String content) throws IOException {
        return Files.write(directory.resolve(name), content.getBytes(UTF_8));
    }

    private String textOf(Path file) {
        ExtractedDocument extracted = extractor.extract(file);
        assertThat(extracted.units()).hasSize(1);
        assertThat(extracted.pageCount()).isNull();
        return extracted.units().getFirst().text();
    }

    @Test
    void readsTheSupportedFormatFromTheTypeItself() {
        assertThat(extractor.supportedType()).isEqualTo(UploadFileType.PLAIN_TEXT);
    }

    @Test
    void readsUtf8TextWithItsAccentedCharacters() {
        String text = textOf(Fixtures.path("plain-text-utf8.txt"));

        assertThat(text)
                .startsWith("Especificación técnica del buscador")
                .contains("La aplicación indexa documentos técnicos")
                .contains("acción, camión, sesión")
                .doesNotContain(REPLACEMENT_CHARACTER);
    }

    /** The reason the charset is detected at all: read as UTF-8, byte 0xE9 decodes to nothing. */
    @Test
    void readsLatin1TextWithItsAccentedCharacters() {
        String text = textOf(Fixtures.path(LATIN_1_FIXTURE));

        assertThat(text)
                .isEqualTo(
                        """
                        Especificación técnica
                        El índice se construye después del análisis.""")
                .contains("é")
                .doesNotContain(REPLACEMENT_CHARACTER);
    }

    /** Guards the fixture: re-saving it as UTF-8 would make the test above pass for the wrong reason. */
    @Test
    void theLatin1FixtureIsStoredAsSingleByteCharacters() {
        byte[] content = Fixtures.bytes(LATIN_1_FIXTURE);

        assertThat(content).contains(E_ACUTE_IN_LATIN_1);
        assertThat(new String(content, ISO_8859_1)).contains("técnica");
        assertThatExceptionOfType(CharacterCodingException.class).isThrownBy(() -> UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(content)));
    }

    @Test
    void failsWithEmptyContentOnAnEmptyFile() throws IOException {
        Path file = write("vacio.txt", "");

        assertThatExceptionOfType(ExtractionFailedException.class)
                .isThrownBy(() -> extractor.extract(file))
                .extracting(ExtractionFailedException::errorCode)
                .isEqualTo(DocumentErrorCode.EMPTY_CONTENT);
    }

    @Test
    void failsWithEmptyContentWhenTheFileOnlyHoldsWhitespace() {
        Path file = Fixtures.path("plain-text-blank.txt");

        assertThatExceptionOfType(ExtractionFailedException.class)
                .isThrownBy(() -> extractor.extract(file))
                .extracting(ExtractionFailedException::errorCode)
                .isEqualTo(DocumentErrorCode.EMPTY_CONTENT);
    }

    @Test
    void dropsTheByteOrderMarkSoTheFirstWordStaysSearchable() throws IOException {
        Path file = write("con-bom.txt", "\uFEFFTítulo del documento");

        assertThat(textOf(file)).isEqualTo("Título del documento");
    }

    @Test
    void normalizesWindowsLineEndingsAndPageBreaks() throws IOException {
        Path file = write("windows.txt", "Primer párrafo\r\n\r\nSegundo párrafo\fTercero\r\n");

        assertThat(textOf(file)).isEqualTo("Primer párrafo\n\nSegundo párrafo\n\nTercero");
    }

    /** A no-break space glues two words into one lexeme, and a NUL makes the insert itself fail. */
    @Test
    void removesTheCharactersThatWouldSpoilTheIndex() throws IOException {
        Path file = write("control.txt", "Antes\u00A0y después\u00ADmente\u0000.");

        assertThat(textOf(file)).isEqualTo("Antes y despuésmente.");
    }
}
