package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.ExtractedDocument;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.UploadFileType;
import java.nio.file.Path;

/**
 * Reads one format. The format stays out of {@link #extract(Path)} so that {@link
 * DispatchingTextExtractor} resolves the reader once and its callers never switch on a type
 * themselves.
 */
public interface FormatTextExtractor {

    UploadFileType supportedType();

    ExtractedDocument extract(Path file);
}
