package de.coldtea.verborum.msuser.common.exception;

/**
 * The caller is authenticated but is acting on a profile that is not theirs. Handled as 403.
 * <p>
 * Deliberately carries no detail about the target: telling a caller whether another user's profile
 * exists is itself a small leak.
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
