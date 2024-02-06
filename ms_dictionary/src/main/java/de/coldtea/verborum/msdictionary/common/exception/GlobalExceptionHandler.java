package de.coldtea.verborum.msdictionary.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import static de.coldtea.verborum.msdictionary.common.constants.ResponseMessageConstants.INTERNAL_SERVER_ERROR;
import static de.coldtea.verborum.msdictionary.common.constants.ResponseMessageConstants.INVALID_UUID_ERROR;

@ControllerAdvice
public class GlobalExceptionHandler {


    // Handling general Exception (catch-all)
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<String> handleException(Exception ex) {
        return new ResponseEntity<>(INTERNAL_SERVER_ERROR + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Handling general Exception (catch-all)
    @ExceptionHandler(InvalidUUIDException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<String> handleInvalidUUIDException(InvalidUUIDException ex) {
        return new ResponseEntity<>(INVALID_UUID_ERROR + ex, HttpStatus.BAD_REQUEST);
    }
}
