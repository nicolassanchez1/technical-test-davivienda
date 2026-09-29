package io.github.nicolassanchez1.technicaltestdavivienda.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DuplicateDocumentException;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class ApiExceptionHandlerTest {

    private static final UUID EXISTING_DOCUMENT_ID = UUID.fromString("1f0d8d0e-6b1a-4f2c-9a4d-0c7e9f5b1a23");

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void reportsAMissingResourceAsNotFound() throws Exception {
        mockMvc.perform(get("/boom/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:resource-not-found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/boom/not-found"));
    }

    @Test
    void carriesTheRequestIdOnEveryProblem() throws Exception {
        mockMvc.perform(get("/boom/not-found").header(RequestIdFilter.REQUEST_ID_HEADER, "trace-9"))
                .andExpect(jsonPath("$.requestId").value("trace-9"));
    }

    @Test
    void reportsADuplicateAsConflict() throws Exception {
        mockMvc.perform(get("/boom/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:problem-type:resource-conflict"));
    }

    @Test
    void reportsADuplicateDocumentWithTheIdThatAlreadyHoldsTheContent() throws Exception {
        mockMvc.perform(get("/boom/duplicate-document"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(DuplicateDocumentException.PROBLEM_TYPE))
                .andExpect(jsonPath("$.existingDocumentId").value(EXISTING_DOCUMENT_ID.toString()));
    }

    @Test
    void reportsAnOversizedUploadAsPayloadTooLarge() throws Exception {
        mockMvc.perform(get("/boom/too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.type").value("urn:problem-type:upload-too-large"));
    }

    @Test
    void reportsAnInvalidArgumentAsBadRequest() throws Exception {
        mockMvc.perform(get("/boom/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem-type:invalid-request"));
    }

    @Test
    void reportsACancelledStatementAsServiceUnavailable() throws Exception {
        mockMvc.perform(get("/boom/timeout"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("urn:problem-type:search-timeout"));
    }

    @Test
    void reportsAWrappedCancelledStatementAsServiceUnavailable() throws Exception {
        mockMvc.perform(get("/boom/wrapped-timeout"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("urn:problem-type:search-timeout"));
    }

    @Test
    void hidesTheDetailOfAnUnexpectedFailure() throws Exception {
        mockMvc.perform(get("/boom/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("urn:problem-type:internal-error"))
                .andExpect(jsonPath("$.detail").value("The request could not be completed."));
    }

    @RestController
    static class FailingController {

        @GetMapping("/boom/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Document 1 does not exist.");
        }

        @GetMapping("/boom/duplicate")
        void duplicate() {
            throw new DataIntegrityViolationException("documents_sha256_unique");
        }

        @GetMapping("/boom/duplicate-document")
        void duplicateDocument() {
            throw new DuplicateDocumentException(EXISTING_DOCUMENT_ID);
        }

        @GetMapping("/boom/too-large")
        void tooLarge() {
            throw new MaxUploadSizeExceededException(1024);
        }

        @GetMapping("/boom/invalid")
        void invalid() {
            throw new IllegalArgumentException("page must be positive");
        }

        @GetMapping("/boom/timeout")
        void timeout() {
            throw new QueryTimeoutException("cancelled");
        }

        @GetMapping("/boom/wrapped-timeout")
        void wrappedTimeout() {
            throw new IllegalStateException(
                    "wrapped", new SQLException("cancelled", ApiExceptionHandler.QUERY_CANCELLED_SQL_STATE));
        }

        @GetMapping("/boom/unexpected")
        void unexpected() {
            throw new IllegalStateException("secret internal detail");
        }
    }
}
