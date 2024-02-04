package de.coldtea.verborum.msdictionary.common.exception;

public class InvalidUUIDException extends IllegalArgumentException{
    public InvalidUUIDException(String fieldName) {
        super(fieldName);
    }
}
