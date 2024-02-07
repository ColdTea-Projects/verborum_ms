package de.coldtea.verborum.msdictionary.common.exception;

import java.io.Serial;

public class InvalidUUIDException extends IllegalArgumentException{
    @Serial
    private static final long serialVersionUID = -5365123123856068000L;

    public InvalidUUIDException(String fieldName) {
        super(fieldName);
    }
}
