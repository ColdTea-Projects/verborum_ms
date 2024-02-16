package de.coldtea.verborum.msdictionary.common.exception;

import de.coldtea.verborum.msdictionary.common.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;

import java.time.OffsetDateTime;
import java.util.List;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, WebRequest request) {
        log.error(Exception.class.getCanonicalName(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, Exception.class.getSimpleName(), ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidUUIDException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleInvalidUUIDException(InvalidUUIDException ex, WebRequest request) {
        log.error(InvalidUUIDException.class.getCanonicalName(), ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, InvalidUUIDException.class.getSimpleName(), ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidLanguageCodeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleInvalidLanguageCodeException(InvalidLanguageCodeException ex, WebRequest request) {
        log.error(InvalidLanguageCodeException.class.getCanonicalName(), ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, InvalidLanguageCodeException.class.getSimpleName(), ex.getMessage(), request);
    }

    private static ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus badRequest, String simpleName, String ex, WebRequest request) {
        return new ResponseEntity<>(
                ErrorResponse.builder()
                        .status(badRequest.value())
                        .error(simpleName)
                        .errorDetail(ex)
                        .path(request.getContextPath())
                        .timestamp(OffsetDateTime.now())
                        .build(),
                badRequest
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex,
                                                                               WebRequest request) {
        log.error(MethodArgumentNotValidException.class.getCanonicalName(), ex);

        List<String> errorMessages = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();

        String errorMessage = String.join(", ", errorMessages);


        return buildErrorResponse(HttpStatus.BAD_REQUEST, errorMessage, MethodArgumentNotValidException.class.getSimpleName(), request);
    }
}
