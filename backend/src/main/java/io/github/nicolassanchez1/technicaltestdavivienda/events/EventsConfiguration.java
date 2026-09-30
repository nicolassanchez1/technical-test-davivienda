package io.github.nicolassanchez1.technicaltestdavivienda.events;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Switches scheduling on for the only task that needs it, the SSE heartbeat, and only where the open
 * streams are. The worker holds no connection to a browser, so it runs no scheduler at all.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("!worker")
class EventsConfiguration {}
