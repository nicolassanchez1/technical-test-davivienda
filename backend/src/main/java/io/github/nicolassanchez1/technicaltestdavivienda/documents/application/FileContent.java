package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import java.io.IOException;
import java.io.InputStream;

/**
 * Opens the bytes of an uploaded file. It hands out a fresh stream on every call, because the
 * upload is read more than once: a prefix to validate it, then the whole file to checksum it and
 * to copy it into storage. Nothing is ever buffered in memory.
 */
@FunctionalInterface
public interface FileContent {

    InputStream open() throws IOException;
}
