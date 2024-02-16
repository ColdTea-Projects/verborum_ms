package de.coldtea.verborum.msdictionary.common.exception;


import java.io.Serial;
public class InvalidLanguageCodeException extends IllegalArgumentException{
    @Serial
    private static final Long serialVersionUID = 1324212415L;

    public InvalidLanguageCodeException(String message) {
        super(message);
    }
}
