package io.github.nicolassanchez1.technicaltestdavivienda.documents.infrastructure;

import java.util.UUID;

/**
 * The body of a {@code documents.process} job. It carries the identifier only: the worker reads
 * the row it points at, so a message that waited in the queue never works from stale metadata.
 */
public record ProcessingJobMessage(UUID documentId) {}
