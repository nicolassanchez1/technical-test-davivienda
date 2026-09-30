package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes down why a document could not be indexed and announces it.
 *
 * <p>It is a use case of its own because two callers need it and both must behave identically: the
 * indexing run that hits a file it can never read, and the queue adapter that has spent every
 * attempt it was given on a failure that looked transient. Either way the reason is committed on
 * its own before the job is abandoned, so a reader is never left watching {@code PROCESANDO}
 * forever.
 */
@Service
public class RecordProcessingFailure {

    /** The column is unbounded, but the reason reaches a UI badge: a paragraph would not fit it. */
    static final int MAX_ERROR_MESSAGE_LENGTH = 300;

    private static final Logger log = LoggerFactory.getLogger(RecordProcessingFailure.class);

    private final DocumentRepository documents;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;

    public RecordProcessingFailure(
            DocumentRepository documents, TransactionTemplate transactions, ApplicationEventPublisher events) {
        this.documents = documents;
        // The reason a document was written off has to survive whatever the caller does next, so it
        // commits in its own transaction rather than inheriting one that may still roll back.
        this.transactions = new TransactionTemplate(transactions.getTransactionManager());
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.events = events;
    }

    /**
     * @return true when this call is the one that moved the document to {@code ERROR}, and false
     *     when the document had already left {@code PROCESANDO}, in which case nothing is announced
     */
    public boolean record(UUID documentId, DocumentErrorCode errorCode, String reason) {
        Boolean recorded = transactions.execute(status -> {
            if (!documents.markFailed(documentId, errorCode, shorten(reason))) {
                return false;
            }
            events.publishEvent(DocumentStatusChanged.failed(documentId, errorCode));
            return true;
        });
        if (Boolean.TRUE.equals(recorded)) {
            log.warn("Document {} could not be indexed: {} ({})", documentId, errorCode, reason);
            return true;
        }
        log.info("Document {} had already left processing, so {} was not recorded", documentId, errorCode);
        return false;
    }

    private static String shorten(String reason) {
        if (reason == null || reason.isBlank()) {
            return "The document could not be processed.";
        }
        String stripped = reason.strip();
        return stripped.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? stripped
                : stripped.substring(0, MAX_ERROR_MESSAGE_LENGTH - 1) + "…";
    }
}
