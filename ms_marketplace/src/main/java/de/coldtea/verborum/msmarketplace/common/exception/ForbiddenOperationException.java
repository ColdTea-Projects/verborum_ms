package de.coldtea.verborum.msmarketplace.common.exception;

/**
 * The caller is authenticated but is acting on data that is not theirs. Handled as 403.
 * <p>
 * Deliberately carries no detail about the target resource — telling a caller whether someone
 * else's data exists is itself a small leak.
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
