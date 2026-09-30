package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import java.util.UUID;

/**
 * Outbound port for handing a document to the worker. Callers never invoke it directly: the use
 * case raises {@link DocumentProcessingRequested} and {@link ProcessingJobDispatcher} calls this
 * once the transaction has committed, so a job can never reference a row that is not yet visible.
 */
public interface JobPublisher {

    void publishProcessingJob(UUID documentId);
}
