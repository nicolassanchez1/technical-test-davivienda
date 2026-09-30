package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

import java.util.List;

/**
 * Raised when an upload is well formed as a request but cannot be processed: the batch itself is
 * out of bounds, or at least one file breaks a rule. Nothing is stored when it is thrown, so a
 * client can correct the offending files and send the whole batch again.
 *
 * <p>It stays free of any HTTP type because the use case that raises it must not depend on the web
 * layer; the single exception handler turns it into a problem document.
 */
public class InvalidUploadException extends RuntimeException {

    public static final String PROBLEM_TYPE = "urn:problem-type:invalid-upload";

    private final List<UploadFileError> errors;

    public InvalidUploadException(String message) {
        this(message, List.of());
    }

    public InvalidUploadException(String message, List<UploadFileError> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    /** One entry per rejected file, in request order. Empty when the batch itself is the problem. */
    public List<UploadFileError> errors() {
        return errors;
    }
}
