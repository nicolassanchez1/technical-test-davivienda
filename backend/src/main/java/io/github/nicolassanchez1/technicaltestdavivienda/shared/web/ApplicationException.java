package io.github.nicolassanchez1.technicaltestdavivienda.shared.web;

import org.springframework.http.HttpStatus;

/**
 * Base class for failures the application reports deliberately. Carrying the status here
 * keeps the exception handler free of per-feature conditionals.
 */
public abstract class ApplicationException extends RuntimeException {

    private final HttpStatus status;
    private final String problemType;

    protected ApplicationException(HttpStatus status, String problemType, String message) {
        super(message);
        this.status = status;
        this.problemType = problemType;
    }

    public HttpStatus status() {
        return status;
    }

    public String problemType() {
        return problemType;
    }
}
