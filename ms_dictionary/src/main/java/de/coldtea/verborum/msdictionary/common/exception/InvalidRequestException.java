package de.coldtea.verborum.msdictionary.common.exception;

/**
 * A request that is well-formed but incomplete for what it would do, in a way bean validation cannot
 * see — e.g. a dictionary created without `isPublic` (P4-18: optional on an update, required on a
 * create, and POST and PUT share one save). Handled as 400 with the field named in the message.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
