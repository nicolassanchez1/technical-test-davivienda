package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.Document;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns one stored file into the chunks the search engine reads, and moves the document to
 * {@code INDEXADO}.
 *
 * <p>Two properties make a repeated delivery of the same job harmless, which matters because a
 * queue guarantees at least one delivery and never exactly one. A document that already reached a
 * terminal state is left alone, and the update that claims the document only matches a row still in
 * <p>Two consumers racing over one document cannot both finish it. Whoever commits second finds
 * the status no longer {@code PROCESANDO}: either its guarded update touches no row and the
 * transaction rolls back, or its chunk insert collides with the winner's on the uniqueness of
 * {@code (document_id, chunk_index)} and the job is redelivered, only to return early because the
 * document is already terminal. Either way the document is indexed once and announced once.
 *
 * <p>Reading and parsing the file happens outside the transaction on purpose: a large PDF takes
 * seconds, and a transaction held open for that long would pin a connection and keep a snapshot
 * alive that nothing needs.
 */
@Service
public class IndexDocument {

    private static final Logger log = LoggerFactory.getLogger(IndexDocument.class);

    private final DocumentRepository documents;
    private final DocumentChunkWriter chunks;
    private final FileStorage storage;
    private final TextExtractor extractor;
    private final DocumentChunker chunker;
    private final RecordProcessingFailure failures;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;

    public IndexDocument(
            DocumentRepository documents,
            DocumentChunkWriter chunks,
            FileStorage storage,
            TextExtractor extractor,
            DocumentChunker chunker,
            RecordProcessingFailure failures,
            TransactionTemplate transactions,
            ApplicationEventPublisher events) {
        this.documents = documents;
        this.chunks = chunks;
        this.storage = storage;
        this.extractor = extractor;
        this.chunker = chunker;
        this.failures = failures;
        this.transactions = transactions;
        this.events = events;
    }

    /**
     * @throws NonRetryableProcessingException when the file can never yield text, after the reason
     *     has been recorded on the document
     * @throws RuntimeException for anything the next attempt may still succeed at, such as the
     *     shared volume not having published the file yet
     */
    public void index(UUID documentId) {
        Optional<Document> found = documents.findById(documentId);
        if (found.isEmpty()) {
            log.warn("Ignoring a job for document {}, which no longer exists", documentId);
            return;
        }
        Document document = found.get();
        if (document.status().isTerminal()) {
            log.info("Repeating the announcement for document {}, already {}", documentId, document.status());
            announceAgain(document);
            return;
        }
        long startedAt = System.nanoTime();
        Extraction extraction = read(document);
        write(document, extraction, elapsedMillis(startedAt));
    }

    /**
     * A job is only redelivered for a finished document when the previous attempt failed after its
     * transaction committed, which is exactly the window where the broadcast can be lost: the
     * document is already indexed and no client was ever told. Repeating the announcement is what
     * closes that window, and a client that did hear the first one discards this by its event id.
     */
    private void announceAgain(Document document) {
        transactions.execute(status -> {
            events.publishEvent(
                    document.status() == DocumentStatus.FAILED
                            ? DocumentStatusChanged.failed(document.id(), document.errorCode())
                            : DocumentStatusChanged.indexed(document.id()));
            return null;
        });
    }

    private Extraction read(Document document) {
        UploadFileType fileType = fileTypeOf(document);
        try {
            Path file = storage.resolve(document.storageKey());
            ExtractedDocument extracted = extractor.extract(file, fileType);
            return new Extraction(extracted.pageCount(), chunker.chunk(extracted, fileType));
        } catch (ExtractionFailedException failure) {
            throw abandon(document.id(), failure.errorCode(), failure.getMessage(), failure);
        }
    }

    /**
     * The stored name carries the extension the upload was accepted as, which the storage itself
     * wrote. The name the client sent is never trusted for this.
     */
    private UploadFileType fileTypeOf(Document document) {
        return UploadFileType.ofFilename(document.storageKey())
                .orElseThrow(() -> abandon(
                        document.id(),
                        DocumentErrorCode.UNSUPPORTED_FORMAT,
                        "No reader claims the stored file " + document.storageKey() + ".",
                        null));
    }

    /**
     * Records the reason before the exception leaves, so the document explains itself to a reader
     * even though its job is dropped.
     */
    private NonRetryableProcessingException abandon(
            UUID documentId, DocumentErrorCode errorCode, String reason, Throwable cause) {
        failures.record(documentId, errorCode, reason);
        return new NonRetryableProcessingException(errorCode, reason, cause);
    }

    private void write(Document document, Extraction extraction, long processingMs) {
        Boolean indexed = transactions.execute(status -> {
            chunks.deleteByDocument(document.id());
            chunks.insertAll(document, extraction.chunks());
            if (!documents.markIndexed(
                    document.id(), extraction.chunks().size(), extraction.pageCount(), processingMs)) {
                // Another consumer finished this document first, so its chunks are the ones to keep.
                status.setRollbackOnly();
                return false;
            }
            events.publishEvent(DocumentStatusChanged.indexed(document.id()));
            return true;
        });
        if (Boolean.TRUE.equals(indexed)) {
            log.info(
                    "Indexed document {} into {} chunk(s) in {} ms",
                    document.id(),
                    extraction.chunks().size(),
                    processingMs);
            return;
        }
        log.info("Discarded a repeated indexing run for document {}: another consumer had finished it", document.id());
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    /** What the file gave up: the chunks to store and the page count only the format knows. */
    private record Extraction(Integer pageCount, List<DocumentChunkDraft> chunks) {}
}
