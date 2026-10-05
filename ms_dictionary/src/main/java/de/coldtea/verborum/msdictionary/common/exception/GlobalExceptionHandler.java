package de.coldtea.verborum.msdictionary.common.exception;

import de.coldtea.verborum.msdictionary.common.response.ErrorResponse;
import de.coldtea.verborum.msdictionary.common.utils.ResponseUtils;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.List;
import static de.coldtea.verborum.msdictionary.common.constants.ErrorMessageConstants.INTERNAL_SERVER_ERROR;
import static de.coldtea.verborum.msdictionary.common.constants.ErrorMessageConstants.METHOD_NOT_ALLOWED;

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

    @ExceptionHandler(RecordNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleRecordNotFoundException(RecordNotFoundException ex, WebRequest request) {
        log.error(RecordNotFoundException.class.getCanonicalName(), ex);
        return buildErrorResponse(HttpStatus.NOT_FOUND, RecordNotFoundException.class.getSimpleName(), ex.getMessage(), request);
    }

    /**
     * A failed constraint on a controller parameter (@ValidUUID / @SupportedLanguage on a path
     * variable) or on an element of a list request body (`POST /words` takes a List of bundles) —
     * Spring MVC's built-in method validation. Added at P4-09: until then a blank word in a word bundle
     * fell through to the catch-all and came back as a 500.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                                WebRequest request) {
        log.warn("{}: {}", HandlerMethodValidationException.class.getCanonicalName(), ex.getMessage());

        List<String> errorMessages = ex.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> errorField(result.getMethodParameter().getParameterName(), error)
                                + ": " + error.getDefaultMessage()))
                .toList();

        return buildErrorResponse(HttpStatus.BAD_REQUEST, HandlerMethodValidationException.class.getSimpleName(),
                String.join(", ", errorMessages), request);
    }

    /**
     * For an element of a list body the failing field is a nested path (e.g. `words[0].word`), which
     * is what the client needs; for a plain parameter it is the parameter name.
     */
    private static String errorField(String parameterName, MessageSourceResolvable error) {
        return error instanceof FieldError fieldError ? parameterName + "." + fieldError.getField() : parameterName;
    }

    private static ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus badRequest, String simpleName, String ex, WebRequest request) {
        return new ResponseEntity<>(
                ErrorResponse.builder()
                        .status(badRequest.value())
                        .error(simpleName)
                        .errorDetail(ex)
                        .path(ResponseUtils.extractPath(request))
                        .timestamp(OffsetDateTime.now())
                        .build(),
                badRequest
        );
    }

    /**
     * The sharing rule (P4-16) — 400 with our own message, safe to return. WARN: a client mistake.
     */
    @ExceptionHandler(SharingRequiredException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleSharingRequiredException(SharingRequiredException ex, WebRequest request) {
        log.warn("{}: {}", SharingRequiredException.class.getCanonicalName(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, SharingRequiredException.class.getSimpleName(), ex.getMessage(), request);
    }

    @ExceptionHandler(QuotaExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleQuotaExceededException(QuotaExceededException ex, WebRequest request) {
        log.warn("{}: {}", QuotaExceededException.class.getCanonicalName(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, QuotaExceededException.class.getSimpleName(), ex.getMessage(), request);
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
}
