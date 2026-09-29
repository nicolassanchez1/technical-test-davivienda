package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The formats the spec accepts. Each one owns the MIME type reported to clients and the canonical
 * extension the stored file is named with, so {@code .markdown} and {@code .md} converge on one
 * predictable layout on disk.
 */
public enum UploadFileType {
    PLAIN_TEXT("text/plain", "txt", Set.of("txt")),
    MARKDOWN("text/markdown", "md", Set.of("md", "markdown")),
    PDF("application/pdf", "pdf", Set.of("pdf"));

    private final String mimeType;
    private final String canonicalExtension;
    private final Set<String> acceptedExtensions;

    UploadFileType(String mimeType, String canonicalExtension, Set<String> acceptedExtensions) {
        this.mimeType = mimeType;
        this.canonicalExtension = canonicalExtension;
        this.acceptedExtensions = acceptedExtensions;
    }

    public String mimeType() {
        return mimeType;
    }

    public String canonicalExtension() {
        return canonicalExtension;
    }

    /** Empty when the filename carries no extension or one outside the allowlist. */
    public static Optional<UploadFileType> ofFilename(String filename) {
        String extension = extensionOf(filename);
        return Arrays.stream(values())
                .filter(type -> type.acceptedExtensions.contains(extension))
                .findFirst();
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0) {
            return "";
        }
        return filename.substring(lastDot + 1).strip().toLowerCase(Locale.ROOT);
    }
}
