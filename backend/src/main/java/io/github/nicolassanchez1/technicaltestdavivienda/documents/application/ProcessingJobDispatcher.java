package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns an upload that has been committed into a processing job.
 *
 * <p>The phase is the whole point. A job published inside the transaction can reach the worker
 * before the row is visible to any other connection, and the worker would then fail to find a
 * document that is about to exist. Binding the publication to {@code AFTER_COMMIT} makes that
 * ordering impossible, and a rolled back upload publishes nothing at all, because the listener is
 * never reached.
 *
 * <p>It depends on the port rather than on a broker, so the guarantee belongs to the use case
 * layer and survives any change of transport.
 */
@Component
public class ProcessingJobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ProcessingJobDispatcher.class);

    private final JobPublisher jobs;

    public ProcessingJobDispatcher(JobPublisher jobs) {
        this.jobs = jobs;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(DocumentProcessingRequested request) {
        log.debug("Publishing the processing job for document {}", request.documentId());
        jobs.publishProcessingJob(request.documentId());
    }
}
