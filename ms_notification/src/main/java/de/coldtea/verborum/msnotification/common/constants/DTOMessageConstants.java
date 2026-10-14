package de.coldtea.verborum.msnotification.common.constants;

/**
 * Validation error messages and request limits used on request parameters and DTOs.
 * Populated as endpoints are added (P4N-03) — see the `web-api` skill.
 */
public final class DTOMessageConstants {

    //@ValidUUID — the field name is prepended by the error handler
    public static final String INVALID_UUID = "must be a valid UUID";

    private DTOMessageConstants() {
    }
}
