package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import java.util.UUID;

/**
 * Raised inside the upload transaction once a document row has been inserted. It is deliberately
 * not the queue message: it only records the intent, and {@link ProcessingJobDispatcher} turns it
 * into a job after the commit.
 */
public record DocumentProcessingRequested(UUID documentId) {}
