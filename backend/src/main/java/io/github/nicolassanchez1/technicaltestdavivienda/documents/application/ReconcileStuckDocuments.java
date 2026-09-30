package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentStatus;
import io.github.nicolassanchez1.technicaltestdavivienda.shared.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Picks up the documents no worker will ever finish.
 *
 * <p>A job can be lost between the two systems that have to agree on it: a worker may be killed
 * between taking a job and committing its result, and a broker restart can outlive a message that
 * was never persisted. Whatever the cause, the row is left saying {@code PROCESANDO} and no one is
 * working on it, so the api asks for it again at boot. Indexing is written to tolerate exactly
 * that, since it replaces the chunks of a document instead of adding to them.
 *
 * <p>It runs on the api side only. A worker reconciling its own queue would republish work it may
 * still be doing, and every worker instance would republish the same documents.
 */
@Component
@Profile("!worker")
public class ReconcileStuckDocuments {

    /**
     * One pass asks for at most this many documents. A backlog that is genuinely this large is a
     * capacity problem, and flooding the queue at boot would only make the worker slower at the
     * jobs it already holds; the next boot picks up whatever is left.
     */
    static final int MAX_DOCUMENTS_PER_PASS = 100;

    private static final Logger log = LoggerFactory.getLogger(ReconcileStuckDocuments.class);

    private final DocumentRepository documents;
    private final JobPublisher jobs;
    private final Duration stuckAfter;

    public ReconcileStuckDocuments(DocumentRepository documents, JobPublisher jobs, AppProperties properties) {
        this.documents = documents;
        this.jobs = jobs;
        this.stuckAfter = Duration.ofMinutes(properties.stuckProcessingMinutes());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        reconcile();
    }

    /**
     * Republishes a job for every document that has been processing for too long.
     *
     * <p>The publication is direct rather than raised as an event, because there is no transaction
     * to wait for: these rows were committed long ago, which is precisely what makes them stuck.
     *
     * @return how many jobs were republished
     */
    public int reconcile() {
        Instant stuckSince = Instant.now().minus(stuckAfter);
        List<UUID> stuck = documents.findStuckInProcessing(stuckSince, MAX_DOCUMENTS_PER_PASS);
        if (stuck.isEmpty()) {
            log.info(
                    "No document has been left in {} for more than {}",
                    DocumentStatus.PROCESSING.wireValue(),
                    stuckAfter);
            return 0;
        }
        stuck.forEach(jobs::publishProcessingJob);
        log.warn(
                "Republished {} job(s) for documents left in {} for more than {}",
                stuck.size(),
                DocumentStatus.PROCESSING.wireValue(),
                stuckAfter);
        log.debug("Republished jobs for {}", stuck);
        return stuck.size();
    }
}
