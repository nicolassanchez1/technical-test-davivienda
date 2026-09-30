package io.github.nicolassanchez1.technicaltestdavivienda.search.application;

/**
 * Raised when a query cannot be searched for: it is empty, or everything in it was dropped by the
 * analyzer. It stays free of any HTTP type because the use case must not depend on the web layer;
 * the single exception handler turns it into a problem document a reader can act on.
 */
public class InvalidSearchQueryException extends RuntimeException {

    public static final String PROBLEM_TYPE = "urn:problem-type:invalid-search-query";

    public InvalidSearchQueryException(String message) {
        super(message);
    }
}
