package io.github.nicolassanchez1.technicaltestdavivienda.events.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.nicolassanchez1.technicaltestdavivienda.events.SseEmitterRegistry;
import io.github.nicolassanchez1.technicaltestdavivienda.shared.config.AppProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The mapping and the headers that decide whether an event ever reaches the browser. */
class EventsControllerTest {

    private SseEmitterRegistry emitters;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        emitters = new SseEmitterRegistry(
                new AppProperties(Path.of("target", "storage"), 20, 10, 900L, 50, 4, 10, 15_000L, 3_600_000L));
        mockMvc =
                MockMvcBuilders.standaloneSetup(new EventsController(emitters)).build();
    }

    @Test
    void answersWithAnEventStreamNoIntermediaryMayBufferOrCache() throws Exception {
        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(header().string("X-Accel-Buffering", "no"));
    }

    @Test
    void registersTheSubscriberSoItReceivesWhatTheBrokerDelivers() throws Exception {
        mockMvc.perform(get("/events"));

        assertThat(emitters.count()).isEqualTo(1);
    }
}
