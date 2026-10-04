package de.coldtea.verborum.msuser.common.exception;

/**
 * Creating or saving a profile would duplicate one that exists: a second profile for the same account
 * (`keycloak_id` is unique), or an email another profile already uses (`email` is unique). Handled as
 * 409 — the request is valid on its own but conflicts with stored data.
 */
public class ProfileConflictException extends RuntimeException {

    public ProfileConflictException(String message) {
        super(message);
    }
}
