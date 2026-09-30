package io.github.nicolassanchez1.technicaltestdavivienda.shared.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.List;

/**
 * The shape every failure is answered with, declared so the generated contract describes it.
 *
 * <p>Nothing constructs this: responses are built as {@link org.springframework.http.ProblemDetail}
 * by {@link ApiExceptionHandler}. It exists because springdoc cannot describe the members a
 * problem detail carries, so without it the contract would type an error body as whatever the
 * method returns on success, and a client would have to hand-write the real shape.
 *
 * @param errors the rejected files of an upload batch, absent on every other failure
 */
@Schema(name = "ProblemDetail", description = "RFC 9457 problem detail")
public record ProblemResponse(
        @Schema(example = "urn:problem-type:invalid-upload", requiredMode = Schema.RequiredMode.REQUIRED) URI type,
        @Schema(example = "Unprocessable Entity", requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(example = "422", requiredMode = Schema.RequiredMode.REQUIRED) int status,
        @Schema(description = "What went wrong, in English; the interface shows its own wording") String detail,
        @Schema(example = "/api/documents") URI instance,
        @Schema(description = "Correlates the failure with the server logs") String requestId,
        List<UploadFileProblem> errors) {

    /** One rejected file of an upload batch, positioned so a client can mark the row it rendered. */
    @Schema(name = "UploadFileProblem", description = "Why one file of a batch was turned down")
    public record UploadFileProblem(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) int index,
            @Schema(example = "manual.exe") String filename,
            @Schema(example = "EXTENSION_ALLOWLIST") String rule,
            @Schema(example = "UNSUPPORTED_FORMAT") String errorCode,
            @Schema(description = "The document that already holds the same content") String existingDocumentId) {}
}
