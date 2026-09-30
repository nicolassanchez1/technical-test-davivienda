package io.github.nicolassanchez1.technicaltestdavivienda.events;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure.DocumentStatusMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The last hop of a status change: from this instance's subscription on the {@code documents.status}
 * fan-out to the browsers it is streaming to.
 *
 * <p>The queue is the exclusive, auto-deleted one declared for this instance. Spring names it on the
 * client side and keeps that name for the life of the process, which is what lets the subscription
 * survive a broker restart: the queue is redeclared under the same name the listener already
 * resolved. Binding a listener to the shared job queue instead would give the event to one instance
 * out of several, and the clients connected to the others would never hear about it.
 */
@Component
@Profile("!worker")
public class DocumentStatusListener {

    private final SseEmitterRegistry emitters;

    public DocumentStatusListener(SseEmitterRegistry emitters) {
        this.emitters = emitters;
    }

    @RabbitListener(queues = "#{documentStatusQueue.name}")
    public void onStatusChanged(DocumentStatusMessage message) {
        emitters.broadcast(message);
    }
}
