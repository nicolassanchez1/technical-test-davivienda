package io.github.nicolassanchez1.technicaltestdavivienda.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    private MockHttpServletResponse filter(String suppliedRequestId, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (suppliedRequestId != null) {
            request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, suppliedRequestId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void generatesAnIdWhenTheCallerSuppliesNone() throws Exception {
        MockHttpServletResponse response = filter(null, (request, ignored) -> {});

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isNotBlank();
    }

    @Test
    void reusesASafeCallerSuppliedId() throws Exception {
        MockHttpServletResponse response = filter("trace-abc_123.4", (request, ignored) -> {});

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("trace-abc_123.4");
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad value", "with\nnewline", "with\rreturn", "<script>", ""})
    void replacesAnIdThatIsNotSafeToEchoBack(String supplied) throws Exception {
        MockHttpServletResponse response = filter(supplied, (request, ignored) -> {});

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isNotEqualTo(supplied);
    }

    @Test
    void replacesAnIdThatExceedsTheLengthCeiling() throws Exception {
        String supplied = "a".repeat(65);

        MockHttpServletResponse response = filter(supplied, (request, ignored) -> {});

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isNotEqualTo(supplied);
    }

    @Test
    void publishesTheIdToTheMdcAndTheRequestWhileTheChainRuns() throws Exception {
        MockHttpServletResponse response = filter("trace-1", (request, ignored) -> {
            assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo("trace-1");
            assertThat(RequestIdFilter.currentRequestId((HttpServletRequest) request))
                    .isEqualTo("trace-1");
        });

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("trace-1");
    }

    @Test
    void clearsTheMdcAfterTheRequest() throws Exception {
        filter("trace-2", (request, ignored) -> {});

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
