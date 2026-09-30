package io.github.nicolassanchez1.technicaltestdavivienda.support;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.UploadFileType;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentCategory;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Documents as the upload leaves them: stored, {@code PROCESANDO}, and waiting for a worker. */
public final class DocumentFixtures {

    private DocumentFixtures() {}

    public static Document processing(String title, String storageKey) {
        return processing(title, storageKey, Instant.now());
    }

    public static Document processing(String title, String storageKey, Instant createdAt) {
        UploadFileType fileType = UploadFileType.ofFilename(storageKey)
                .orElseThrow(
                        () -> new IllegalArgumentException("Not a storage key of an accepted format: " + storageKey));
        return new Document(
                UUID.randomUUID(),
                title,
                "Equipo de plataforma",
                DocumentCategory.SPECIFICATION,
                List.of("busqueda", "postgresql"),
                "1.0",
                "documento." + fileType.canonicalExtension(),
                fileType.mimeType(),
                2048,
                storageKey,
                randomChecksum(),
                DocumentStatus.PROCESSING,
                null,
                null,
                null,
                null,
                null,
                createdAt,
                createdAt,
                null);
    }

    public static String storageKey(String extension) {
        return UUID.randomUUID() + "." + extension;
    }

    /** Two random UUIDs without their dashes are exactly the 64 hex characters the column holds. */
    public static String randomChecksum() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }
}
