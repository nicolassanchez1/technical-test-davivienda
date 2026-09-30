package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.DocumentStatusChanged;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.StatusEventPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Broadcasts a status change to every api instance through the {@code documents.status} fanout
 * exchange. A fanout needs no routing key, and the exchange holds nothing: an instance that is not
 * running at the time has no browser to notify either, and the reconciling fetch an SSE client runs
 * on reconnect is what covers the gap.
 *
 * <p>It is only ever reached from the after-commit listener, so an event never announces a state
 * the database has not committed.
 */
@Component
public class RabbitStatusEventPublisher implements StatusEventPublisher {

    private static final String FANOUT_ROUTING_KEY = "";

    private final RabbitTemplate rabbitTemplate;

    public RabbitStatusEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishStatusChanged(DocumentStatusChanged event) {
        rabbitTemplate.convertAndSend(
                DocumentsMessagingConfiguration.STATUS_EXCHANGE,
                FANOUT_ROUTING_KEY,
                DocumentStatusMessage.of(event),
                message -> {
                    message.getMessageProperties()
                            .setMessageId(event.documentId().toString());
                    return message;
                });
    }
}
