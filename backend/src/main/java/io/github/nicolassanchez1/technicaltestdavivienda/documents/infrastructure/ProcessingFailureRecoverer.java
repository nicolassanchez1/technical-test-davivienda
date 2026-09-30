package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.RecordProcessingFailure;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DocumentErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * What happens to a job that has used up every attempt it was given. The document is marked with
 * {@code PROCESSING_FAILED} so that a reader stops waiting, and the message is then rejected
 * without being requeued, which routes it to {@code documents.process.dlq} for inspection.
 *
 * <p>A job that failed deterministically arrives here too, because the policy refuses to retry it.
 * That one already carries the error code its extraction produced, so it is only dead-lettered.
 */
@Component
@Profile("worker")
public class ProcessingFailureRecoverer extends RejectAndDontRequeueRecoverer {

    private static final Logger log = LoggerFactory.getLogger(ProcessingFailureRecoverer.class);

    private final RecordProcessingFailure failures;

    public ProcessingFailureRecoverer(RecordProcessingFailure failures) {
        this.failures = failures;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        if (!alreadyRecorded(cause)) {
            documentIdOf(message)
                    .ifPresent(documentId ->
                            failures.record(documentId, DocumentErrorCode.PROCESSING_FAILED, reasonOf(cause)));
        }
        super.recover(message, cause);
    }

    private static boolean alreadyRecorded(Throwable cause) {
        for (Throwable current = cause; current != null; current = nextCause(current)) {
            if (current instanceof AmqpRejectAndDontRequeueException) {
                return true;
            }
        }
        return false;
    }

    private static Throwable nextCause(Throwable current) {
        return current.getCause() == current ? null : current.getCause();
    }

    /**
     * The job carries its document id as the message id, so a message whose body could not even be
     * read is still attributable. When it is not, the document stays {@code PROCESANDO} and the
     * reconciler on the api side is what picks it up again.
     */
    private static Optional<UUID> documentIdOf(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null) {
            log.error("Dropping a job that carries no message id, so it names no document");
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(messageId));
        } catch (IllegalArgumentException unusable) {
            log.error("Dropping a job whose message id is not a document: {}", messageId);
            return Optional.empty();
        }
    }

    private static String reasonOf(Throwable cause) {
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
