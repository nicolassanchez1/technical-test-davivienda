package io.github.nicolassanchez1.technicaltestdavivienda.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * A context that consumes the job queue, which the api never does: the listener, its container and
 * its retry policy only exist under the {@code worker} profile.
 *
 * <p>The annotations are restated rather than inherited because the worker is a different process.
 * It has no web environment at all, and Flyway is the one thing the profile cannot keep switched
 * off here: production hands the worker a database the api has already migrated, while a test
 * starts with an empty container and has to build the schema itself.
 */
@ActiveProfiles("worker")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.flyway.enabled=true")
public abstract class AbstractWorkerIntegrationTest extends AbstractIntegrationTest {}
