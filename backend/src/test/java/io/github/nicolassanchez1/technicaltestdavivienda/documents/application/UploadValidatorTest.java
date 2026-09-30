package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.shared.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UploadValidatorTest {

    private static final int MAX_FILE_SIZE_MB = 1;
    private static final long MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024L * 1024L;

    private final UploadValidator validator = new UploadValidator(new AppProperties(
            Path.of("target", "storage"), MAX_FILE_SIZE_MB, 10, 900L, 50, 4, 10, 15_000L, 3_600_000L));

    private static byte[] utf8(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private UploadValidation validate(String filename, byte[] content) {
        return validator.validate(filename, content.length, content);
    }

    @Test
    void acceptsAPlainTextFile() {
        UploadValidation validation = validate("notas.txt", utf8("Especificación técnica\n"));

        assertThat(validation.isAccepted()).isTrue();
        assertThat(validation.fileType()).isEqualTo(UploadFileType.PLAIN_TEXT);
        assertThat(validation.fileType().mimeType()).isEqualTo("text/plain");
        assertThat(validation.fileType().canonicalExtension()).isEqualTo("txt");
        assertThat(validation.errorCode()).isNull();
    }

    @Test
    void acceptsAMarkdownFile() {
        UploadValidation validation = validate("guia.md", utf8("# Guía\n\nContenido\n"));

        assertThat(validation.isAccepted()).isTrue();
        assertThat(validation.fileType()).isEqualTo(UploadFileType.MARKDOWN);
        assertThat(validation.fileType().mimeType()).isEqualTo("text/markdown");
    }

    @Test
    void acceptsTheLongMarkdownExtensionUnderTheCanonicalOne() {
        UploadValidation validation = validate("guia.markdown", utf8("# Guía\n"));

        assertThat(validation.fileType()).isEqualTo(UploadFileType.MARKDOWN);
        assertThat(validation.fileType().canonicalExtension()).isEqualTo("md");
    }

    @Test
    void acceptsAPdfThatStartsWithItsHeader() {
        UploadValidation validation = validate("manual.pdf", utf8("%PDF-1.7\n%¿÷¢\n1 0 obj\n"));

        assertThat(validation.isAccepted()).isTrue();
        assertThat(validation.fileType()).isEqualTo(UploadFileType.PDF);
        assertThat(validation.fileType().mimeType()).isEqualTo("application/pdf");
    }

    @Test
    void ignoresTheCaseOfTheExtension() {
        assertThat(validate("MANUAL.PDF", utf8("%PDF-1.4\n")).isAccepted()).isTrue();
        assertThat(validate("NOTAS.TXT", utf8("contenido")).isAccepted()).isTrue();
    }

    @Test
    void acceptsTextThatIsNotUtf8BecauseTheCharsetIsDetectedLater() {
        byte[] latin1 = "Especificación técnica\r\n".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(validate("notas.txt", latin1).isAccepted()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"informe.docx", "captura.png", "archivo", "backup.tar.gz", "notas."})
    void rejectsAnExtensionOutsideTheAllowlist(String filename) {
        UploadValidation validation = validate(filename, utf8("contenido"));

        assertThat(validation.isAccepted()).isFalse();
        assertThat(validation.fileType()).isNull();
        assertThat(validation.failedRule()).isEqualTo(UploadRule.EXTENSION_ALLOWLIST);
        assertThat(validation.errorCode()).isEqualTo(DocumentErrorCode.UNSUPPORTED_FORMAT);
    }

    @Test
    void rejectsAnEmptyFile() {
        UploadValidation validation = validator.validate("notas.txt", 0, new byte[0]);

        assertThat(validation.failedRule()).isEqualTo(UploadRule.NON_EMPTY_FILE);
        assertThat(validation.errorCode()).isEqualTo(DocumentErrorCode.EMPTY_CONTENT);
    }

    @Test
    void rejectsAFileBeyondTheConfiguredCeiling() {
        UploadValidation validation = validator.validate("notas.txt", MAX_FILE_SIZE_BYTES + 1, utf8("contenido"));

        assertThat(validation.failedRule()).isEqualTo(UploadRule.MAXIMUM_FILE_SIZE);
        assertThat(validation.errorCode()).isNull();
    }

    @Test
    void acceptsAFileExactlyAtTheConfiguredCeiling() {
        UploadValidation validation = validator.validate("notas.txt", MAX_FILE_SIZE_BYTES, utf8("contenido"));

        assertThat(validation.isAccepted()).isTrue();
    }

    @Test
    void rejectsAPdfWhoseHeaderDoesNotMatch() {
        UploadValidation validation = validate("manual.pdf", utf8("Esto no es un PDF"));

        assertThat(validation.failedRule()).isEqualTo(UploadRule.PDF_HEADER);
        assertThat(validation.errorCode()).isEqualTo(DocumentErrorCode.CORRUPT_FILE);
    }

    @Test
    void rejectsAPdfTooShortToCarryItsHeader() {
        UploadValidation validation = validator.validate("manual.pdf", 3, utf8("%PD"));

        assertThat(validation.failedRule()).isEqualTo(UploadRule.PDF_HEADER);
    }

    @Test
    void rejectsATextFileHoldingANulByte() {
        byte[] withNul = {'h', 'o', 'l', 'a', 0, 'm', 'u', 'n', 'd', 'o'};

        UploadValidation validation = validate("notas.txt", withNul);

        assertThat(validation.failedRule()).isEqualTo(UploadRule.TEXT_WITHOUT_BINARY_BYTES);
        assertThat(validation.errorCode()).isEqualTo(DocumentErrorCode.CORRUPT_FILE);
    }

    @Test
    void rejectsMarkdownHoldingABinaryControlByte() {
        byte[] withControlByte = {'#', ' ', 'G', 'u', 'i', 'a', 0x07};

        assertThat(validate("guia.md", withControlByte).failedRule()).isEqualTo(UploadRule.TEXT_WITHOUT_BINARY_BYTES);
    }

    @Test
    void stopsInspectingTextOnceThePrefixCeilingIsReached() {
        byte[] longPrefix = new byte[UploadValidator.INSPECTED_PREFIX_BYTES + 1];
        Arrays.fill(longPrefix, (byte) 'a');
        longPrefix[UploadValidator.INSPECTED_PREFIX_BYTES] = 0;

        assertThat(validate("notas.txt", longPrefix).isAccepted()).isTrue();
    }

    @Test
    void acceptsTextHoldingTabsAndLineBreaks() {
        byte[] whitespace = {'a', '\t', 'b', '\r', '\n', 'c', '\f'};

        assertThat(validate("notas.txt", whitespace).isAccepted()).isTrue();
    }
}
