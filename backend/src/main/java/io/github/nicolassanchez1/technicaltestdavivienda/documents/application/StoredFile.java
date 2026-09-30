package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import java.nio.file.Path;

/** A document together with the bytes that were uploaded for it, ready to be streamed back. */
public record StoredFile(Document document, Path path) {}
