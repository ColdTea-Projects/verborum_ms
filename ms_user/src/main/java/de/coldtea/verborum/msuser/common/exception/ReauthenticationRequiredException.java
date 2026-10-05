package de.coldtea.verborum.msuser.common.exception;

/**
 * The action needs a recent login and the token's is too old (SEC-13) — today only account deletion.
 * Handled as 403, deliberately not 401: both clients refresh and retry on a 401, and a refresh never
 * makes a login recent, so a 401 would loop. The client signs the user in again (`max_age=0`) and
 * retries with the new token.
 */
public class ReauthenticationRequiredException extends RuntimeException {

    public ReauthenticationRequiredException(String message) {
        super(message);
    }
}
