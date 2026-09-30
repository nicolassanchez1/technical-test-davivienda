package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentMetadata;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.github.nicolassanchez1.technicaltestdavivienda.shared.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepts one or more files with their metadata, records them as {@code PROCESANDO} and asks for
 * them to be processed.
 *
 * <p>The batch is all or nothing. Every file is validated and checksummed before a single byte is
 * written, so a request that carries one broken file leaves no half-finished upload behind: no
 * stored file, no row, no job. Whatever does get stored is undone if the transaction fails, since
 * a filesystem does not roll back on its own.
 */
@Service
public class UploadDocuments {

    private static final Logger log = LoggerFactory.getLogger(UploadDocuments.class);

    private final UploadValidator validator;
    private final DocumentRepository documents;
    private final FileStorage storage;
    private final ApplicationEventPublisher events;
    private final int maxFilesPerUpload;

    public UploadDocuments(
            UploadValidator validator,
            DocumentRepository documents,
            FileStorage storage,
            ApplicationEventPublisher events,
            AppProperties properties) {
        this.validator = validator;
        this.documents = documents;
        this.storage = storage;
        this.events = events;
        this.maxFilesPerUpload = properties.maxFilesPerUpload();
    }

    /**
     * @return the stored documents, in the order the files were sent
     * @throws InvalidUploadException when the batch is out of bounds or any file breaks a rule
     */
    @Transactional
    public List<Document> upload(List<UploadedFile> files, List<DocumentMetadata> metadata) {
        rejectUnusableBatch(files, metadata);
        return store(accept(files), metadata);
    }

    private void rejectUnusableBatch(List<UploadedFile> files, List<DocumentMetadata> metadata) {
        if (files == null || files.isEmpty()) {
            throw new InvalidUploadException("An upload must carry at least one file.");
        }
        if (files.size() > maxFilesPerUpload) {
            throw new InvalidUploadException("An upload carries at most %d files, and this one carries %d."
                    .formatted(maxFilesPerUpload, files.size()));
        }
        int entries = metadata == null ? 0 : metadata.size();
        if (entries != files.size()) {
            throw new InvalidUploadException(
                    "Every file needs one metadata entry at the same position: %d files and %d entries."
                            .formatted(files.size(), entries));
        }
    }

    /** Reports every rejected file at once, so a client fixes the whole batch in one round trip. */
    private List<AcceptedUpload> accept(List<UploadedFile> files) {
        List<AcceptedUpload> accepted = new ArrayList<>(files.size());
        List<UploadFileError> errors = new ArrayList<>();
        Set<String> checksumsInThisBatch = new HashSet<>();

        for (int index = 0; index < files.size(); index++) {
            UploadedFile file = files.get(index);
            UploadValidation validation = validator.validate(file.filename(), file.sizeBytes(), leadingBytes(file));
            if (!validation.isAccepted()) {
                errors.add(UploadFileError.rejectedBy(index, file.filename(), validation.failedRule()));
                continue;
            }
            String sha256 = checksumOf(file);
            Optional<UUID> alreadyStored = documents.findIdBySha256(sha256);
            if (alreadyStored.isPresent()) {
                errors.add(UploadFileError.duplicateOf(index, file.filename(), alreadyStored.get()));
                continue;
            }
            // The unique index on sha256 is the real guard, but a violation aborts the whole
            // PostgreSQL transaction, which would take the other files of the batch down with it.
            // Two copies of the same content in one request are therefore caught here.
            if (!checksumsInThisBatch.add(sha256)) {
                errors.add(UploadFileError.duplicateOf(index, file.filename(), null));
                continue;
            }
            accepted.add(new AcceptedUpload(index, file, validation.fileType(), sha256));
        }

        if (!errors.isEmpty()) {
            throw new InvalidUploadException("One or more files were rejected, so nothing was stored.", errors);
        }
        return accepted;
    }

    private List<Document> store(List<AcceptedUpload> accepted, List<DocumentMetadata> metadata) {
        List<String> storageKeys = new ArrayList<>(accepted.size());
        try {
            List<Document> stored = new ArrayList<>(accepted.size());
            for (AcceptedUpload upload : accepted) {
                String storageKey = storeContent(upload);
                storageKeys.add(storageKey);
                Document saved = documents.save(documentOf(upload, metadata.get(upload.index()), storageKey));
                events.publishEvent(new DocumentProcessingRequested(saved.id()));
                stored.add(saved);
            }
            return List.copyOf(stored);
        } catch (RuntimeException failure) {
            storageKeys.forEach(this::discard);
            throw failure;
        }
    }

    private String storeContent(AcceptedUpload upload) {
        try (InputStream content = upload.file().content().open()) {
            return storage.store(content, upload.fileType().canonicalExtension());
        } catch (IOException cause) {
            throw new UncheckedIOException("Could not read " + upload.file().filename(), cause);
        }
    }

    private byte[] leadingBytes(UploadedFile file) {
        try (InputStream content = file.content().open()) {
            return content.readNBytes(UploadValidator.INSPECTED_PREFIX_BYTES);
        } catch (IOException cause) {
            throw new UncheckedIOException("Could not read " + file.filename(), cause);
        }
    }

    private String checksumOf(UploadedFile file) {
        try (InputStream content = file.content().open()) {
            return Sha256Digest.of(content);
        } catch (IOException cause) {
            throw new UncheckedIOException("Could not read " + file.filename(), cause);
        }
    }

    /** A failed cleanup must not replace the failure that caused it. */
    private void discard(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException cause) {
            log.warn("Could not remove {} after a failed upload", storageKey, cause);
        }
    }

    private static Document documentOf(AcceptedUpload upload, DocumentMetadata metadata, String storageKey) {
        Instant now = Instant.now();
        return new Document(
                UUID.randomUUID(),
                metadata.title(),
                metadata.author(),
                metadata.category(),
                metadata.tags(),
                metadata.version(),
                upload.file().filename(),
                upload.fileType().mimeType(),
                upload.file().sizeBytes(),
                storageKey,
                upload.sha256(),
                DocumentStatus.PROCESSING,
                null,
                null,
                null,
                null,
                null,
                now,
                now,
                null);
    }

    /** A file that passed every rule, together with what validating it already found out. */
    private record AcceptedUpload(int index, UploadedFile file, UploadFileType fileType, String sha256) {}
}
