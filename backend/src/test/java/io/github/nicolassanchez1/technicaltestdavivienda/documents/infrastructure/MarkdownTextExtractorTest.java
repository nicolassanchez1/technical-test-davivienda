package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractedDocument;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractedUnit;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractionFailedException;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.UploadFileType;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.support.Fixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownTextExtractorTest {

    private static final String FIXTURE = "markdown-sections.md";

    @TempDir
    Path directory;

    private final MarkdownTextExtractor extractor = new MarkdownTextExtractor(new TextFileDecoder());

    private ExtractedDocument fixture() {
        return extractor.extract(Fixtures.path(FIXTURE));
    }

    private static String indexedText(ExtractedDocument extracted) {
        return String.join(
                "\n", extracted.units().stream().map(ExtractedUnit::text).toList());
    }

    @Test
    void readsTheSupportedFormatFromTheTypeItself() {
        assertThat(extractor.supportedType()).isEqualTo(UploadFileType.MARKDOWN);
    }

    @Test
    void emitsOneUnitPerSectionCarryingItsHeading() {
        ExtractedDocument extracted = fixture();

        assertThat(extracted.pageCount()).isNull();
        assertThat(extracted.units())
                .extracting(ExtractedUnit::heading)
                .containsExactly(null, "Guía de despliegue", "Requisitos", "Variables de entorno", "Verificación");
        assertThat(extracted.units()).extracting(ExtractedUnit::page).containsOnlyNulls();
    }

    @Test
    void keepsTextBeforeTheFirstHeadingInASectionWithoutOne() {
        ExtractedUnit preamble = fixture().units().getFirst();

        assertThat(preamble.heading()).isNull();
        assertThat(preamble.text())
                .isEqualTo("Resumen del documento con una palabra destacada antes de cualquier título.");
    }

    @Test
    void repeatsTheHeadingInsideItsSectionSoAMatchOnItIsHighlighted() {
        ExtractedUnit section = fixture().units().get(1);

        assertThat(section.text()).startsWith("Guía de despliegue\n\nLa guía cubre el despliegue");
    }

    @Test
    void stripsTheMarkupFromTheIndexedText() {
        String text = indexedText(fixture());

        assertThat(text)
                .contains("palabra destacada")
                .contains("PostgreSQL 17 con la extensión unaccent")
                .doesNotContain("**")
                .doesNotContain("#")
                .doesNotContain("`")
                .doesNotContain("- PostgreSQL");
    }

    @Test
    void keepsTheLinkTextAndDropsTheUrl() {
        String text = indexedText(fixture());

        assertThat(text).contains("la documentación oficial amplía cada paso");
        assertThat(text).doesNotContain("https://").doesNotContain("example.test");
    }

    @Test
    void keepsTheAlternativeTextOfAnImageAndDropsItsSource() {
        String text = indexedText(fixture());

        assertThat(text).contains("Diagrama de la arquitectura").doesNotContain("arquitectura.png");
    }

    @Test
    void keepsCodeFenceContentAsPlainText() {
        ExtractedUnit section = fixture().units().get(3);

        assertThat(section.text())
                .contains("CREATE EXTENSION IF NOT EXISTS unaccent;")
                .contains("SELECT set_config('statement_timeout', '900', true);");
    }

    @Test
    void leavesRawHtmlOutOfTheIndexedText() {
        assertThat(indexedText(fixture())).doesNotContain("div").doesNotContain("Este HTML no se indexa");
    }

    /** Below h3 a heading titles a detail, so its text stays inside the section that encloses it. */
    @Test
    void foldsHeadingsDeeperThanTheThirdLevelIntoTheirSection() {
        ExtractedUnit section = fixture().units().get(3);

        assertThat(section.heading()).isEqualTo("Variables de entorno");
        assertThat(section.text()).contains("Valores por defecto").contains("El puerto de la API es 8081");
    }

    @Test
    void failsWithEmptyContentWhenTheMarkdownCarriesNoText() throws IOException {
        Path file = Files.write(directory.resolve("solo-separadores.md"), List.of("", "***", "", "---", ""));

        assertThatExceptionOfType(ExtractionFailedException.class)
                .isThrownBy(() -> extractor.extract(file))
                .extracting(ExtractionFailedException::errorCode)
                .isEqualTo(DocumentErrorCode.EMPTY_CONTENT);
    }
}
