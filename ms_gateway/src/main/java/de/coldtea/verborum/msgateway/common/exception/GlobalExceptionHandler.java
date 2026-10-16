package de.coldtea.verborum.msgateway.common.exception;

import de.coldtea.verborum.msgateway.common.response.ErrorResponse;
import de.coldtea.verborum.msgateway.common.utils.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;

import static de.coldtea.verborum.msgateway.common.constants.ErrorMessageConstants.INTERNAL_SERVER_ERROR;
import static de.coldtea.verborum.msgateway.common.constants.ErrorMessageConstants.METHOD_NOT_ALLOWED;

/**
 * The gateway's own failures in the usual error envelope. Upstream errors pass through unchanged;
 * the upstream-unreachable 502/504 handlers arrive with the routes (P5-02).
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Catch-all. Does not put `ex.getMessage()` on the wire — an unhandled exception's message is
     * unvetted and can carry internals. The full exception is logged; the caller gets a fixed string.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, WebRequest request) {
        log.error(Exception.class.getCanonicalName(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, Exception.class.getSimpleName(), INTERNAL_SERVER_ERROR, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex, WebRequest request) {
        log.warn("{}: {}", HttpRequestMethodNotSupportedException.class.getCanonicalName(), ex.getMessage());
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, HttpRequestMethodNotSupportedException.class.getSimpleName(), METHOD_NOT_ALLOWED, request);
    }

    /**
     * A path that matches no route. Without this it falls into the catch-all and a plain 404 is
     * reported as a 500. WARN, not ERROR: an unknown URL is a client mistake or a scanner.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException ex, WebRequest request) {
        log.warn("{}: {}", NoResourceFoundException.class.getCanonicalName(), ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, NoResourceFoundException.class.getSimpleName(), ex.getMessage(), request);
    }

    private static ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String simpleName, String ex, WebRequest request) {
        return new ResponseEntity<>(
                ErrorResponse.builder()
                        .status(status.value())
                        .error(simpleName)
                        .errorDetail(ex)
                        .path(ResponseUtils.extractPath(request))
                        .timestamp(OffsetDateTime.now())
                        .build(),
                status
        );
    }
}
