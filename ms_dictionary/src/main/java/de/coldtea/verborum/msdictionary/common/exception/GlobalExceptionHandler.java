package de.coldtea.verborum.msdictionary.common.exception;

import de.coldtea.verborum.msdictionary.common.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;

import java.time.OffsetDateTime;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, WebRequest request) {
        log.error(Exception.class.getCanonicalName(), ex);
        return new ResponseEntity<>(
                ErrorResponse.builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .error(Exception.class.getSimpleName())
                        .errorDetail(ex.getMessage())
                        .path(request.getContextPath())
                        .timestamp(OffsetDateTime.now())
                        .build(),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(InvalidUUIDException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleInvalidUUIDException(InvalidUUIDException ex, WebRequest request) {
        log.error(InvalidUUIDException.class.getCanonicalName(), ex);
        return new ResponseEntity<>(
                ErrorResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(InvalidUUIDException.class.getSimpleName())
                        .errorDetail(ex.getMessage())
                        .path(request.getContextPath())
                        .timestamp(OffsetDateTime.now())
                        .build(),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(InvalidLanguageCodeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleInvalidLanguageCodeException(InvalidLanguageCodeException ex, WebRequest request) {
        log.error(InvalidLanguageCodeException.class.getCanonicalName(), ex);
        return new ResponseEntity<>(
                ErrorResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(InvalidLanguageCodeException.class.getSimpleName())
                        .errorDetail(ex.getMessage())
                        .path(request.getContextPath())
                        .timestamp(OffsetDateTime.now())
                        .build(),
                HttpStatus.BAD_REQUEST
        );
    }
}
