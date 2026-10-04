package de.coldtea.verborum.msuser.common.exception;

/**
 * A profile change would break a profile rule (P4-14): accepting the marketplace terms without a
 * display name or terms version, or removing the display name while the terms are accepted. Handled
 * as 400 — the request is wrong, not the caller's rights.
 */
public class InvalidProfileException extends RuntimeException {

    public InvalidProfileException(String message) {
        super(message);
    }
}
