package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import static java.util.function.Function.identity;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractedDocument;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractionFailedException;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.TextExtractor;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.UploadFileType;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The single {@link TextExtractor} the pipeline sees: it picks the reader that claims the format and
 * hands the file over. Adding a format means adding a {@link FormatTextExtractor}, and two readers
 * claiming one format fail the context at startup rather than silently shadowing each other.
 */
@Component
public class DispatchingTextExtractor implements TextExtractor {

    private final Map<UploadFileType, FormatTextExtractor> extractorsByType;

    public DispatchingTextExtractor(List<FormatTextExtractor> extractors) {
        this.extractorsByType = extractors.stream()
                .collect(Collectors.toUnmodifiableMap(FormatTextExtractor::supportedType, identity()));
    }

    @Override
    public ExtractedDocument extract(Path file, UploadFileType fileType) {
        FormatTextExtractor extractor = extractorsByType.get(fileType);
        if (extractor == null) {
            throw new ExtractionFailedException(
                    DocumentErrorCode.UNSUPPORTED_FORMAT, "No extractor reads " + fileType + " files.");
        }
        requireReadable(file);
        return extractor.extract(file);
    }

    /**
     * A file that is not there says nothing about the document: the shared volume may still be
     * catching up, so this stays an I/O failure the caller may retry instead of a verdict on the
     * content.
     */
    private static void requireReadable(Path file) {
        if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new UncheckedIOException(new IOException("The stored file cannot be read: " + file));
        }
    }
}
