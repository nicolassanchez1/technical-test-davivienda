package io.github.nicolassanchez1.technicaltestdavivienda.shared.web;

import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.InvalidUploadException;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.application.UploadFileError;
import io.github.nicolassanchez1.technicaltestdavivienda.documents.domain.DuplicateDocumentException;
import io.github.nicolassanchez1.technicaltestdavivienda.search.application.InvalidSearchQueryException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Renders every failure as RFC 9457 problem+json, always carrying the request id so a
 * report from a user can be traced straight to the log line that produced it.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** PostgreSQL reports a cancelled statement, which is how the search timeout surfaces. */
    static final String QUERY_CANCELLED_SQL_STATE = "57014";

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApplicationException.class)
    ProblemDetail handleApplicationException(ApplicationException exception, HttpServletRequest request) {
        return problem(exception.status(), exception.problemType(), exception.getMessage(), request);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class,
        IllegalArgumentException.class
    })
    ProblemDetail handleInvalidRequest(Exception exception, HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST, "urn:problem-type:invalid-request", "The request is not valid.", request);
        if (exception instanceof MethodArgumentNotValidException validation) {
            problem.setProperty("errors", fieldErrors(validation));
        }
        return problem;
    }

    /**
     * Mapped on its own rather than through the generic bad request, because the caller can only
     * fix a query that carries no searchable term if the answer says so.
     */
    @ExceptionHandler(InvalidSearchQueryException.class)
    ProblemDetail handleInvalidSearchQuery(InvalidSearchQueryException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST, InvalidSearchQueryException.PROBLEM_TYPE, exception.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleUnknownRoute(NoResourceFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND, "urn:problem-type:resource-not-found", "The resource does not exist.", request);
    }

    /**
     * The checksum uniqueness rule lives in the domain, so its exception is mapped here rather than
     * carrying an HTTP status of its own: the domain stays free of the web layer.
     */
    @ExceptionHandler(DuplicateDocumentException.class)
    ProblemDetail handleDuplicateDocument(DuplicateDocumentException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.CONFLICT,
                DuplicateDocumentException.PROBLEM_TYPE,
                "A document with the same content is already stored.",
                request);
        problem.setProperty("existingDocumentId", exception.existingDocumentId().toString());
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "urn:problem-type:resource-conflict",
                "The resource conflicts with one that already exists.",
                request);
    }

    /**
     * A batch is all or nothing: every rejected file is reported at once so the caller fixes the
     * whole upload in one round trip instead of discovering the next problem on the next attempt.
     */
    @ExceptionHandler(InvalidUploadException.class)
    ProblemDetail handleInvalidUpload(InvalidUploadException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.UNPROCESSABLE_ENTITY, InvalidUploadException.PROBLEM_TYPE, exception.getMessage(), request);
        if (!exception.errors().isEmpty()) {
            problem.setProperty(
                    "errors",
                    exception.errors().stream()
                            .map(ApiExceptionHandler::describe)
                            .toList());
        }
        return problem;
    }

    private static Map<String, Object> describe(UploadFileError error) {
        Map<String, Object> described = new LinkedHashMap<>();
        described.put("index", error.index());
        described.put("filename", error.filename());
        described.put("rule", error.rule().name());
        if (error.errorCode() != null) {
            described.put("errorCode", error.errorCode().name());
        }
        if (error.existingDocumentId() != null) {
            described.put("existingDocumentId", error.existingDocumentId().toString());
        }
        return described;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "urn:problem-type:upload-too-large",
                "The uploaded file exceeds the configured maximum size.",
                request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "urn:problem-type:unsupported-media-type",
                "The request media type is not supported.",
                request);
    }

    @ExceptionHandler(QueryTimeoutException.class)
    ProblemDetail handleQueryTimeout(QueryTimeoutException exception, HttpServletRequest request) {
        log.warn("Query exceeded its statement timeout", exception);
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "urn:problem-type:search-timeout",
                "The query took too long and was cancelled. Narrow the search and retry.",
                request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        if (isCancelledStatement(exception)) {
            return handleQueryTimeout(new QueryTimeoutException("Statement cancelled", exception), request);
        }
        log.error("Unhandled failure while serving {}", request.getRequestURI(), exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "urn:problem-type:internal-error",
                "The request could not be completed.",
                request);
    }

    private static boolean isCancelledStatement(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException
                    && QUERY_CANCELLED_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
            if (current.getCause() == current) {
                return false;
            }
        }
        return false;
    }

    private static Map<String, String> fieldErrors(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception
                .getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage() == null ? "is not valid" : error.getDefaultMessage()));
        return errors;
    }

    private static ProblemDetail problem(HttpStatus status, String type, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setInstance(URI.create(request.getRequestURI()));
        String requestId = RequestIdFilter.currentRequestId(request);
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }
}
