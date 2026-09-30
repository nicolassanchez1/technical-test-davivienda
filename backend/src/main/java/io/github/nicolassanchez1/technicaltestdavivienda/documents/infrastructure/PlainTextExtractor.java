package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractedDocument;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractedUnit;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractionFailedException;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.UploadFileType;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Reads a plain text file. It has no structure to report, so the whole file is one unit and the
 * chunker is what decides where the cuts go.
 */
@Component
public class PlainTextExtractor implements FormatTextExtractor {

    private final TextFileDecoder decoder;

    public PlainTextExtractor(TextFileDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public UploadFileType supportedType() {
        return UploadFileType.PLAIN_TEXT;
    }

    @Override
    public ExtractedDocument extract(Path file) {
        String text = TextNormalizer.normalize(decoder.decode(file));
        if (text.isBlank()) {
            throw new ExtractionFailedException(
                    DocumentErrorCode.EMPTY_CONTENT, "The file holds no text to index: " + file.getFileName());
        }
        return ExtractedDocument.of(List.of(ExtractedUnit.of(text)));
    }
}
