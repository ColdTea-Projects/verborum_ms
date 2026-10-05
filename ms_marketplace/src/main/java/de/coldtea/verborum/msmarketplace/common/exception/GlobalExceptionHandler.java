package de.coldtea.verborum.msmarketplace.common.exception;

import de.coldtea.verborum.msmarketplace.common.response.ErrorResponse;
import de.coldtea.verborum.msmarketplace.common.utils.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static de.coldtea.verborum.msmarketplace.common.constants.ErrorMessageConstants.INTERNAL_SERVER_ERROR;
import static de.coldtea.verborum.msmarketplace.common.constants.ErrorMessageConstants.METHOD_NOT_ALLOWED;
import static de.coldtea.verborum.msmarketplace.common.constants.ErrorMessageConstants.INVALID_PARAMETER;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Central exception handler. Add an @ExceptionHandler here for every new exception type
 * introduced by entities/endpoints (see clean-code.md).
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Catch-all. <b>Does not put `ex.getMessage()` on the wire</b> — an unhandled exception is by
     * definition one nobody vetted the message of, and those messages carry internals: a Postgres
     * constraint violation names the table, column and constraint, an NPE names a field. The full
     * exception is logged; the caller gets a fixed string.
     * <p>
     * The specific handlers below do return `ex.getMessage()`, and that is safe because their
     * messages are our own constants.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, WebRequest request) {
        log.error(Exception.class.getCanonicalName(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, Exception.class.getSimpleName(), INTERNAL_SERVER_ERROR, request);
    }

    @ExceptionHandler(RecordNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleRecordNotFoundException(RecordNotFoundException ex, WebRequest request) {
        log.error(RecordNotFoundException.class.getCanonicalName(), ex);
        return buildErrorResponse(HttpStatus.NOT_FOUND, RecordNotFoundException.class.getSimpleName(), ex.getMessage(), request);
    }


    /**
     * A known path called with a method it does not have — e.g. the vault's removed POST (SEC-09).
     * Without this the catch-all answered 500 and logged a stack trace for a plain client mistake.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex, WebRequest request) {
        log.warn("{}: {}", HttpRequestMethodNotSupportedException.class.getCanonicalName(), ex.getMessage());
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, HttpRequestMethodNotSupportedException.class.getSimpleName(), METHOD_NOT_ALLOWED, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, WebRequest request) {
        log.error(HttpMessageNotReadableException.class.getCanonicalName(), ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, HttpMessageNotReadableException.class.getSimpleName(), ex.getMessage(), request);
    }

    @ExceptionHandler(SelfImportException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleSelfImportException(SelfImportException ex, WebRequest request) {
        log.warn("{}: {}", SelfImportException.class.getCanonicalName(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, SelfImportException.class.getSimpleName(), ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<ErrorResponse> handleForbiddenOperationException(ForbiddenOperationException ex, WebRequest request) {
        log.warn("{}: {}", ForbiddenOperationException.class.getCanonicalName(), ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ForbiddenOperationException.class.getSimpleName(), ex.getMessage(), request);
    }

    /**
     * A request for a path that does not map to anything. Without this handler it falls into the
     * generic `Exception` handler and a plain 404 is reported as a 500 — the same class of bug as
     * P0-14, found at P3-06 when `/actuator/env` stopped being exposed and started returning
     * "No static resource actuator/env" as a server error.
     * <p>
     * Logged at WARN, not ERROR: an unknown URL is a client mistake (or a scanner), not a fault.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException ex, WebRequest request) {
        log.warn("{}: {}", NoResourceFoundException.class.getCanonicalName(), ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, NoResourceFoundException.class.getSimpleName(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex,
                                                                                 WebRequest request) {
        log.error(MethodArgumentNotValidException.class.getCanonicalName(), ex);

        List<String> errorMessages = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();

        String errorMessage = String.join(", ", errorMessages);

        return buildErrorResponse(HttpStatus.BAD_REQUEST, MethodArgumentNotValidException.class.getSimpleName(), errorMessage, request);
    }

    /**
     * A failed constraint on a controller parameter (@Min/@Max on paging, @SupportedLanguage on the
     * language filter) — Spring MVC's built-in method validation (P4-06). Without this handler it
     * fell through to the catch-all and a bad `size` was a 500.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                                WebRequest request) {
        log.warn("{}: {}", HandlerMethodValidationException.class.getCanonicalName(), ex.getMessage());

        List<String> errorMessages = ex.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> result.getMethodParameter().getParameterName() + ": " + error.getDefaultMessage()))
                .toList();

        return buildErrorResponse(HttpStatus.BAD_REQUEST, HandlerMethodValidationException.class.getSimpleName(),
                String.join(", ", errorMessages), request);
    }

    /**
     * A required query parameter is absent (e.g. `from` on the language filter) — 400, not 500.
     * <p>
     * `ex.getMessage()` is safe here, unlike in the type-mismatch handler below: a parameter that is
     * absent has no value to echo, so Spring's message names only the parameter and its type.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex,
                                                                                       WebRequest request) {
        log.warn("{}: {}", MissingServletRequestParameterException.class.getCanonicalName(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, MissingServletRequestParameterException.class.getSimpleName(),
                ex.getMessage(), request);
    }

    /**
     * A parameter that does not convert (e.g. `page=abc`) — 400, not 500. The message is built from
     * the parameter name only: the exception's own message quotes the raw input back.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex,
                                                                                   WebRequest request) {
        log.warn("{}: {}", MethodArgumentTypeMismatchException.class.getCanonicalName(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, MethodArgumentTypeMismatchException.class.getSimpleName(),
                INVALID_PARAMETER + ex.getName(), request);
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
