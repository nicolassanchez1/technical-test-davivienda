package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The invariants the chunker relies on: no blank unit, no empty document, no page number below one. */
class ExtractedDocumentTest {

    @Test
    void reportsPagesOnlyForAFormatThatHasThem() {
        ExtractedDocument paged = ExtractedDocument.paged(List.of(ExtractedUnit.onPage(1, "Página")), 12);
        ExtractedDocument plain = ExtractedDocument.of(List.of(ExtractedUnit.of("Texto")));

        assertThat(paged.pageCount()).isEqualTo(12);
        assertThat(plain.pageCount()).isNull();
    }

    @Test
    void countsThePagesOfTheFileAndNotTheUnitsItYielded() {
        ExtractedDocument extracted = ExtractedDocument.paged(List.of(ExtractedUnit.onPage(3, "Sólo esta página")), 9);

        assertThat(extracted.units()).hasSize(1);
        assertThat(extracted.pageCount()).isEqualTo(9);
    }

    @Test
    void refusesADocumentWithoutUnits() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ExtractedDocument.of(List.of()));
    }

    @Test
    void refusesAUnitWithoutText() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ExtractedUnit.of("  \n\t "));
    }

    @Test
    void refusesAPageNumberBelowOneBecausePagesAreCountedAsAReaderCountsThem() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ExtractedUnit.onPage(0, "Texto"));
    }

    @Test
    void treatsABlankHeadingAsNoHeadingAtAll() {
        assertThat(ExtractedUnit.underHeading("   ", "Texto").heading()).isNull();
        assertThat(ExtractedUnit.underHeading("  Requisitos  ", "Texto").heading())
                .isEqualTo("Requisitos");
    }

    @Test
    void doesNotLetTheCallerChangeTheUnitsAfterwards() {
        List<ExtractedUnit> units = new java.util.ArrayList<>(List.of(ExtractedUnit.of("Texto")));
        ExtractedDocument extracted = ExtractedDocument.of(units);

        units.add(ExtractedUnit.of("Añadido después"));

        assertThat(extracted.units()).hasSize(1);
    }
}
