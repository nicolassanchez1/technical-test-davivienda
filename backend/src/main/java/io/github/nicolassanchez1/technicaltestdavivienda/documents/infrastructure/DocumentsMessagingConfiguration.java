package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The processing topology, declared as beans so the broker is ready before the first upload rather
 * than at the first message. Nothing consumes {@code documents.process} yet; the worker joins in a
 * later phase and finds the queue already in place.
 *
 * <p>The queue is durable and carries persistent messages, so a broker restart does not lose jobs.
 * A job the worker rejects for good is routed to {@code documents.process.dlq} instead of being
 * redelivered forever, which keeps a poisoned message out of the retry loop and inspectable.
 */
@Configuration
public class DocumentsMessagingConfiguration {

    public static final String PROCESS_QUEUE = "documents.process";
    public static final String DEAD_LETTER_EXCHANGE = "documents.process.dlx";
    public static final String DEAD_LETTER_QUEUE = "documents.process.dlq";

    /** Jobs go straight to the queue through the default exchange, which needs no routing rule. */
    public static final String DEFAULT_EXCHANGE = "";

    @Bean
    Queue documentsProcessQueue() {
        return QueueBuilder.durable(PROCESS_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    DirectExchange documentsProcessDeadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    Queue documentsProcessDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding documentsProcessDeadLetterBinding() {
        return BindingBuilder.bind(documentsProcessDeadLetterQueue())
                .to(documentsProcessDeadLetterExchange())
                .with(DEAD_LETTER_QUEUE);
    }

    /** JSON on the wire keeps a job readable in the management console and in the dead-letter queue. */
    @Bean
    MessageConverter amqpMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
