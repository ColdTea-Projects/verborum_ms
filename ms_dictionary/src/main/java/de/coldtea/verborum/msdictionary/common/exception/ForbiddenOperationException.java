package de.coldtea.verborum.msdictionary.common.exception;

/**
 * The caller is authenticated but is acting on data that is not theirs. Handled as 403.
 * <p>
 * Deliberately carries no detail about the target resource — telling a caller whether someone
 * else's dictionary exists is itself a small leak.
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
