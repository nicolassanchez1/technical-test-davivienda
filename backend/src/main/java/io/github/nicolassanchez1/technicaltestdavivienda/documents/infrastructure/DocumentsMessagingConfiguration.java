package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * The messaging topology, declared as beans so the broker is ready before the first upload rather
 * than at the first message.
 *
 * <p>Jobs travel on a durable queue carrying persistent messages, so a broker restart does not lose
 * them. A job the worker rejects for good is routed to {@code documents.process.dlq} instead of
 * being redelivered forever, which keeps a poisoned message out of the retry loop and inspectable.
 *
 * <p>Status changes travel the other way, on a fanout exchange. A queue per api instance rather
 * than one shared queue is what the SSE fan-out needs: every instance has to see every event,
 * because it only knows the browsers connected to itself.
 */
@Configuration
public class DocumentsMessagingConfiguration {

    public static final String PROCESS_QUEUE = "documents.process";
    public static final String DEAD_LETTER_EXCHANGE = "documents.process.dlx";
    public static final String DEAD_LETTER_QUEUE = "documents.process.dlq";
    public static final String STATUS_EXCHANGE = "documents.status";

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

    @Bean
    FanoutExchange documentsStatusExchange() {
        return ExchangeBuilder.fanoutExchange(STATUS_EXCHANGE).durable(true).build();
    }

    /** JSON on the wire keeps a job readable in the management console and in the dead-letter queue. */
    @Bean
    MessageConverter amqpMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * The subscription an api instance holds on the status fan-out. The queue is exclusive and auto
     * deleted, so each instance gets its own copy of every event and the broker is left clean when
     * that instance stops. The worker publishes to the exchange and never binds to it.
     */
    @Configuration
    @Profile("!worker")
    static class StatusSubscriptionConfiguration {

        @Bean
        Queue documentStatusQueue() {
            return new AnonymousQueue();
        }

        @Bean
        Binding documentStatusBinding(FanoutExchange documentsStatusExchange) {
            return BindingBuilder.bind(documentStatusQueue()).to(documentsStatusExchange);
        }
    }
}
