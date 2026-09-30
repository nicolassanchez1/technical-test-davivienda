package io.github.nicolassanchez1.technicaltestdavivienda.support;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;

/** Keeps every application event a use case raised, in order, for the test to assert on. */
public final class RecordingEventPublisher implements ApplicationEventPublisher {

    private final List<Object> published = new ArrayList<>();

    @Override
    public void publishEvent(Object event) {
        published.add(event);
    }

    public <T> List<T> eventsOfType(Class<T> type) {
        return published.stream().filter(type::isInstance).map(type::cast).toList();
    }

    public List<Object> published() {
        return List.copyOf(published);
    }
}
