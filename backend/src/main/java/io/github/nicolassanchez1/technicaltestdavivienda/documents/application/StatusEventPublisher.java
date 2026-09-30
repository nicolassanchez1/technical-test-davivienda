package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

/**
 * Outbound port for broadcasting a status change to every api instance, each of which forwards it
 * to the browsers it holds an SSE connection with. Callers never invoke it directly: they raise
 * {@link DocumentStatusChanged} and {@link StatusEventDispatcher} publishes after the commit.
 */
public interface StatusEventPublisher {

    void publishStatusChanged(DocumentStatusChanged event);
}
