package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Outbound port for the bytes of an uploaded file. The storage key is opaque to the caller, so a
 * shared directory can be swapped for object storage without changing the use cases.
 */
public interface FileStorage {

    /**
     * Streams the content into storage under a generated key and returns that key. The name the
     * client sent is never reused.
     */
    String store(InputStream content, String extension);

    /** The local path of a stored file. Rejects any key the storage did not generate. */
    Path resolve(String storageKey);

    void delete(String storageKey);
}
