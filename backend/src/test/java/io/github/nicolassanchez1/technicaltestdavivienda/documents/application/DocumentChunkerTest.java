package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import static io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentChunker.MAX_CHUNK_CHARACTERS;
import static io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentChunker.MAX_CHUNK_WORDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker();

    private static String paragraphOf(int words, String marker) {
        return marker + " " + "palabra ".repeat(words - 1).strip();
    }

    private static int wordsIn(String content) {
        return content.strip().split("\\s+").length;
    }

    private static List<Integer> indexesOf(List<DocumentChunkDraft> chunks) {
        return chunks.stream().map(DocumentChunkDraft::chunkIndex).toList();
    }

    @Test
    void emitsOneChunkPerPdfPage() {
        ExtractedDocument extracted = ExtractedDocument.paged(
                List.of(
                        ExtractedUnit.onPage(1, "Introducción al manual"),
                        ExtractedUnit.onPage(2, "Requisitos del sistema"),
                        ExtractedUnit.onPage(4, "Anexo final")),
                4);

        List<DocumentChunkDraft> chunks = chunker.chunk(extracted, UploadFileType.PDF);

        assertThat(chunks).hasSize(3);
        assertThat(indexesOf(chunks)).containsExactly(0, 1, 2);
        assertThat(chunks).extracting(DocumentChunkDraft::page).containsExactly(1, 2, 4);
        assertThat(chunks).extracting(DocumentChunkDraft::heading).containsOnlyNulls();
        assertThat(chunks.getFirst().content()).isEqualTo("Introducción al manual");
    }

    @Test
    void emitsOneChunkPerMarkdownSection() {
        ExtractedDocument extracted = ExtractedDocument.of(List.of(
                ExtractedUnit.underHeading(null, "Resumen del documento"),
                ExtractedUnit.underHeading("Requisitos", "PostgreSQL 17 y RabbitMQ 4"),
                ExtractedUnit.underHeading("Despliegue", "docker compose up --build")));

        List<DocumentChunkDraft> chunks = chunker.chunk(extracted, UploadFileType.MARKDOWN);

        assertThat(chunks).hasSize(3);
        assertThat(indexesOf(chunks)).containsExactly(0, 1, 2);
        assertThat(chunks).extracting(DocumentChunkDraft::heading).containsExactly(null, "Requisitos", "Despliegue");
        assertThat(chunks).extracting(DocumentChunkDraft::page).containsOnlyNulls();
    }

    @Test
    void packsPlainTextParagraphsUpToTheWordCeilingWithoutCuttingOne() {
        String text = IntStream.rangeClosed(1, 4)
                .mapToObj(number -> paragraphOf(600, "Parrafo-" + number))
                .reduce((left, right) -> left + "\n\n" + right)
                .orElseThrow();

        List<DocumentChunkDraft> chunks =
                chunker.chunk(ExtractedDocument.of(List.of(ExtractedUnit.of(text))), UploadFileType.PLAIN_TEXT);

        assertThat(chunks).hasSize(2);
        assertThat(indexesOf(chunks)).containsExactly(0, 1);
        assertThat(chunks)
                .allSatisfy(chunk -> assertThat(wordsIn(chunk.content())).isLessThanOrEqualTo(MAX_CHUNK_WORDS));
        assertThat(chunks.get(0).content())
                .contains("Parrafo-1")
                .contains("Parrafo-2")
                .doesNotContain("Parrafo-3");
        assertThat(chunks.get(1).content()).contains("Parrafo-3").contains("Parrafo-4");
    }

    @Test
    void cutsAParagraphThatAlreadyPassesTheWordCeilingOnItsOwn() {
        String text = paragraphOf(2_000, "Parrafo-unico");

        List<DocumentChunkDraft> chunks =
                chunker.chunk(ExtractedDocument.of(List.of(ExtractedUnit.of(text))), UploadFileType.PLAIN_TEXT);

        assertThat(chunks).hasSize(2);
        assertThat(wordsIn(chunks.get(0).content())).isEqualTo(MAX_CHUNK_WORDS);
        assertThat(wordsIn(chunks.get(1).content())).isEqualTo(500);
    }

    /** A page is one chunk, unless the page alone is too much text for one. */
    @Test
    void splitsASinglePageThatPassesTheCharacterCap() {
        String page = "palabra ".repeat(MAX_CHUNK_CHARACTERS / 4).strip();

        List<DocumentChunkDraft> chunks =
                chunker.chunk(ExtractedDocument.paged(List.of(ExtractedUnit.onPage(7, page)), 1), UploadFileType.PDF);

        assertThat(chunks).hasSize(2);
        assertThat(indexesOf(chunks)).containsExactly(0, 1);
        assertThat(chunks).extracting(DocumentChunkDraft::page).containsOnly(7);
        assertThat(chunks)
                .allSatisfy(chunk -> assertThat(chunk.content().length()).isLessThanOrEqualTo(MAX_CHUNK_CHARACTERS));
        // Splitting happens at whitespace, so putting the pieces back gives the page again.
        assertThat(String.join(
                        " ", chunks.stream().map(DocumentChunkDraft::content).toList()))
                .isEqualTo(page);
    }

    @Test
    void cutsAtTheCapItselfWhenThereIsNoWhitespaceToCutAt() {
        String unbroken = "x".repeat(MAX_CHUNK_CHARACTERS + 500);

        List<DocumentChunkDraft> chunks = chunker.chunk(
                ExtractedDocument.paged(List.of(ExtractedUnit.onPage(1, unbroken)), 1), UploadFileType.PDF);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).content()).hasSize(MAX_CHUNK_CHARACTERS);
        assertThat(chunks.get(1).content()).hasSize(500);
    }

    @Test
    void numbersChunksContiguouslyFromZeroAcrossUnitsThatSplitAndUnitsThatDoNot() {
        ExtractedDocument extracted = ExtractedDocument.of(List.of(
                ExtractedUnit.underHeading("Resumen", "Una sola sección corta"),
                ExtractedUnit.underHeading(
                        "Detalle", "palabra ".repeat(MAX_CHUNK_CHARACTERS / 4).strip()),
                ExtractedUnit.underHeading("Cierre", "Fin del documento")));

        List<DocumentChunkDraft> chunks = chunker.chunk(extracted, UploadFileType.MARKDOWN);

        assertThat(chunks).hasSize(4);
        assertThat(indexesOf(chunks)).containsExactly(0, 1, 2, 3);
        assertThat(chunks)
                .extracting(DocumentChunkDraft::heading)
                .containsExactly("Resumen", "Detalle", "Detalle", "Cierre");
    }

    /** A run of whitespace can fill a whole window, and an empty chunk would index nothing. */
    @Test
    void dropsWindowsThatHoldOnlyWhitespaceAndKeepsTheIndexesContiguous() {
        String sparse = "Inicio" + " ".repeat(MAX_CHUNK_CHARACTERS * 2 + 100) + "Final";

        List<DocumentChunkDraft> chunks =
                chunker.chunk(ExtractedDocument.paged(List.of(ExtractedUnit.onPage(1, sparse)), 1), UploadFileType.PDF);

        assertThat(chunks).extracting(DocumentChunkDraft::content).containsExactly("Inicio", "Final");
        assertThat(indexesOf(chunks)).containsExactly(0, 1);
    }

    @Test
    void keepsAShortPlainTextFileAsASingleChunk() {
        String text = "Primer párrafo del documento.\n\nSegundo párrafo del documento.";

        List<DocumentChunkDraft> chunks =
                chunker.chunk(ExtractedDocument.of(List.of(ExtractedUnit.of(text))), UploadFileType.PLAIN_TEXT);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().content()).isEqualTo(text);
        assertThat(chunks.getFirst().page()).isNull();
        assertThat(chunks.getFirst().heading()).isNull();
    }

    @Test
    void rejectsAChunkThatWouldIndexNothing() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DocumentChunkDraft(0, null, null, "   \n "));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DocumentChunkDraft(-1, null, null, "contenido"));
    }
}
