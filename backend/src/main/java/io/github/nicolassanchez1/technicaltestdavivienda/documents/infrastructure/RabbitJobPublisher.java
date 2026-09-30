package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.JobPublisher;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes a processing job to RabbitMQ. It is only ever reached from the after-commit listener,
 * so by the time a message exists the row it names is already visible to the worker.
 */
@Component
public class RabbitJobPublisher implements JobPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitJobPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishProcessingJob(UUID documentId) {
        rabbitTemplate.convertAndSend(
                DocumentsMessagingConfiguration.DEFAULT_EXCHANGE,
                DocumentsMessagingConfiguration.PROCESS_QUEUE,
                new ProcessingJobMessage(documentId),
                message -> {
                    // The document id doubles as the message id, so one job can be traced from the
                    // management console to the row without opening the body.
                    message.getMessageProperties().setMessageId(documentId.toString());
                    return message;
                });
    }
}
