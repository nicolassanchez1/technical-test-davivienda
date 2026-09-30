package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import java.nio.file.Path;

/**
 * Outbound port that turns a stored file into text. The caller hands over the path the storage
 * resolved and the format the upload was accepted as, and gets back the units in reading order, so
 * choosing a reader per format stays inside the adapter.
 */
public interface TextExtractor {

    /**
     * @throws ExtractionFailedException when the file cannot yield indexable text, naming the
     *     deterministic reason
     * @throws java.io.UncheckedIOException when the file itself could not be read, which a caller
     *     may retry
     */
    ExtractedDocument extract(Path file, UploadFileType fileType);
}
