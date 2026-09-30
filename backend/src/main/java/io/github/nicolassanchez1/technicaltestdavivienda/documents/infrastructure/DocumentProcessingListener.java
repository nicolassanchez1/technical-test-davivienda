package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.IndexDocument;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.NonRetryableProcessingException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The worker's only entry point: a job on {@code documents.process} indexes the document it names.
 *
 * <p>The translation is the whole body of the method. The use case reports a failure no retry can
 * fix by its own exception, and this turns it into the one exception the broker understands as
 * "reject this, do not send it again", which routes the job to the dead-letter queue. A failure of
 * any other kind travels untouched, so the retry advice around this listener gets to see it.
 */
@Component
@Profile("worker")
public class DocumentProcessingListener {

    private final IndexDocument indexDocument;

    public DocumentProcessingListener(IndexDocument indexDocument) {
        this.indexDocument = indexDocument;
    }

    @RabbitListener(
            queues = DocumentsMessagingConfiguration.PROCESS_QUEUE,
            containerFactory = WorkerMessagingConfiguration.PROCESSING_CONTAINER_FACTORY)
    public void onProcessingJob(ProcessingJobMessage job) {
        try {
            indexDocument.index(job.documentId());
        } catch (NonRetryableProcessingException failure) {
            throw new AmqpRejectAndDontRequeueException(failure.getMessage(), failure);
        }
    }
}
