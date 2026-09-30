package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Broadcasts a status change once the transaction that produced it has committed.
 *
 * <p>The phase carries the same guarantee as the upload side. An event sent from inside the
 * transaction can reach a browser before the row is visible, and the reader would then fetch a
 * document that is still {@code PROCESANDO}; worse, a transaction that rolls back would have
 * announced a state that never existed. Binding the broadcast to {@code AFTER_COMMIT} rules both
 * out.
 */
@Component
public class StatusEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(StatusEventDispatcher.class);

    private final StatusEventPublisher statusEvents;

    public StatusEventDispatcher(StatusEventPublisher statusEvents) {
        this.statusEvents = statusEvents;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(DocumentStatusChanged event) {
        log.debug("Broadcasting {} for document {}", event.status(), event.documentId());
        statusEvents.publishStatusChanged(event);
    }
}
