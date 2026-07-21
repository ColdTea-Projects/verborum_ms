package de.coldtea.verborum.msuser.common.exception;

import de.coldtea.verborum.msuser.common.response.ErrorResponse;
import de.coldtea.verborum.msuser.common.utils.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Central exception handler. Add an @ExceptionHandler here for every new exception type
 * introduced by entities/endpoints (see clean-code.md).
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, WebRequest request) {
        log.error(Exception.class.getCanonicalName(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, Exception.class.getSimpleName(), ex.getMessage(), request);
    }

    @ExceptionHandler(RecordNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleRecordNotFoundException(RecordNotFoundException ex, WebRequest request) {
        log.error(RecordNotFoundException.class.getCanonicalName(), ex);
        return buildErrorResponse(HttpStatus.NOT_FOUND, RecordNotFoundException.class.getSimpleName(), ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidUUIDException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleInvalidUUIDException(InvalidUUIDException ex, WebRequest request) {
        log.error(InvalidUUIDException.class.getCanonicalName(), ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, InvalidUUIDException.class.getSimpleName(), ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, WebRequest request) {
        log.error(HttpMessageNotReadableException.class.getCanonicalName(), ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, HttpMessageNotReadableException.class.getSimpleName(), ex.getMessage(), request);
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
