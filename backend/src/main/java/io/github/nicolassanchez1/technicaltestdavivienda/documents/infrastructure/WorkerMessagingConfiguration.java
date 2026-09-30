package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import io.github.nicolassanchez1.technicaltestdavivienda.shared.config.AppProperties;
import java.time.Duration;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * How the worker consumes {@code documents.process}. It exists only under the {@code worker}
 * profile, so an api instance never competes for a job.
 *
 * <p>Retrying is deliberately narrow. A file that cannot yield text fails the same way on every
 * delivery, so the use case records the reason and the listener raises the exception that tells the
 * broker to dead-letter the job. Everything else, a volume that has not published the file yet or a
 * database that is briefly away, gets a few more chances before the document is given up on.
 */
@Configuration
@Profile("worker")
public class WorkerMessagingConfiguration {

    public static final String PROCESSING_CONTAINER_FACTORY = "documentProcessingContainerFactory";

    /** The first delivery plus two more. A fourth would only lengthen the wait before the DLQ. */
    static final int MAX_ATTEMPTS = 3;

    private static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(1);
    private static final double RETRY_MULTIPLIER = 2.0;
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(10);

    @Bean(PROCESSING_CONTAINER_FACTORY)
    SimpleRabbitListenerContainerFactory documentProcessingContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            AppProperties properties,
            MessageRecoverer recoverer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setConcurrentConsumers(properties.workerConcurrency());
        factory.setMaxConcurrentConsumers(properties.workerConcurrency());
        // One unacknowledged job per consumer. With a larger window a consumer that draws one
        // 400 page PDF would hold the jobs queued behind it while its neighbours sit idle.
        factory.setPrefetchCount(1);
        // Nothing is ever put back on the queue by rejection: a job either succeeds, is retried in
        // place, or lands in the dead-letter queue where it can be inspected.
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(retryInterceptor(recoverer));
        return factory;
    }

    /**
     * Retries in place, with a growing pause between attempts. The exclusion is what separates the
     * two kinds of failure: a job the listener has already given a verdict on is handed straight to
     * the recoverer instead of being tried again.
     */
    private static MethodInterceptor retryInterceptor(MessageRecoverer recoverer) {
        return RetryInterceptorBuilder.stateless()
                .configureRetryPolicy(policy -> policy.maxRetries(MAX_ATTEMPTS - 1)
                        .delay(FIRST_RETRY_DELAY)
                        .multiplier(RETRY_MULTIPLIER)
                        .maxDelay(MAX_RETRY_DELAY)
                        .excludes(AmqpRejectAndDontRequeueException.class))
                .recoverer(recoverer)
                .build();
    }
}
