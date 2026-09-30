package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentChunk;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read side of the documents module: list, detail, body and original file.
 *
 * <p>Commands get a use case of their own because they carry invariants. These four are thin
 * projections with none, so grouping them keeps the module readable while still leaving the
 * controller talking to the application layer instead of reaching for a repository.
 */
@Service
@Transactional(readOnly = true)
public class DocumentCatalog {

    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final FileStorage storage;

    public DocumentCatalog(DocumentRepository documents, DocumentChunkRepository chunks, FileStorage storage) {
        this.documents = documents;
        this.chunks = chunks;
        this.storage = storage;
    }

    /** Newest first. A null status lists documents in every status. */
    public DocumentPage list(DocumentStatus statusOrNull, int limit, int offset) {
        return new DocumentPage(documents.findAll(statusOrNull, limit, offset), documents.countAll(statusOrNull));
    }

    public Optional<Document> byId(UUID documentId) {
        return documents.findById(documentId);
    }

    /**
     * Empty when the document does not exist, which is what separates a missing document from one
     * whose body has not been indexed yet: the latter answers with no chunks and no cursor.
     */
    public Optional<DocumentContent> content(UUID documentId, int fromChunkIndex, int limit) {
        if (documents.findById(documentId).isEmpty()) {
            return Optional.empty();
        }
        // One extra chunk is asked for, and never returned: it only says whether a next page exists.
        List<DocumentChunk> found = chunks.findByDocument(documentId, fromChunkIndex, limit + 1);
        if (found.size() <= limit) {
            return Optional.of(new DocumentContent(found, null));
        }
        List<DocumentChunk> page = found.subList(0, limit);
        return Optional.of(new DocumentContent(page, page.getLast().chunkIndex() + 1));
    }

    /** Empty when the document does not exist or its bytes are no longer on the shared volume. */
    public Optional<StoredFile> file(UUID documentId) {
        return documents
                .findById(documentId)
                .map(document -> new StoredFile(document, storage.resolve(document.storageKey())))
                .filter(stored -> Files.isReadable(stored.path()));
    }
}
