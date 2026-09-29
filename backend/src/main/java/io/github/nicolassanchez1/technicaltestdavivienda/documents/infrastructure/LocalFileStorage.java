package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.FileStorage;
import io.github.nicolassanchez1.technicaltestdavivienda.shared.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Keeps uploads in the directory the api and the worker share. A file is named after a fresh UUID,
 * so the name the client sent never reaches the filesystem and cannot collide with another upload,
 * overwrite an existing file or point outside the storage directory.
 */
@Component
public class LocalFileStorage implements FileStorage {

    private static final Pattern STORAGE_KEY =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.[a-z0-9]{1,16}$");
    private static final Pattern EXTENSION = Pattern.compile("^[a-z0-9]{1,16}$");

    private final Path storageDir;

    public LocalFileStorage(AppProperties properties) {
        this.storageDir = properties.storageDir().toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageDir);
        } catch (IOException cause) {
            throw new UncheckedIOException("Could not create the storage directory " + storageDir, cause);
        }
    }

    @Override
    public String store(InputStream content, String extension) {
        String storageKey = UUID.randomUUID() + "." + canonicalExtension(extension);
        try {
            Files.copy(content, storageDir.resolve(storageKey));
        } catch (IOException cause) {
            throw new UncheckedIOException("Could not store the upload as " + storageKey, cause);
        }
        return storageKey;
    }

    /**
     * Only a bare {@code <uuid>.<ext>} name is accepted. The pattern admits no separator and no dot
     * segment, so a crafted key cannot walk out of the storage directory.
     */
    @Override
    public Path resolve(String storageKey) {
        if (storageKey == null || !STORAGE_KEY.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("Not a storage key: " + storageKey);
        }
        return storageDir.resolve(storageKey);
    }

    @Override
    public void delete(String storageKey) {
        Path stored = resolve(storageKey);
        try {
            Files.deleteIfExists(stored);
        } catch (IOException cause) {
            throw new UncheckedIOException("Could not delete " + storageKey, cause);
        }
    }

    private static String canonicalExtension(String extension) {
        String value = extension == null ? "" : extension.strip().toLowerCase(Locale.ROOT);
        if (value.startsWith(".")) {
            value = value.substring(1);
        }
        if (!EXTENSION.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a usable file extension: " + extension);
        }
        return value;
    }
}
