package de.coldtea.verborum.msnotification.common.constants;

/**
 * Validation error messages and request limits used on request parameters and DTOs.
 * Populated as endpoints are added (P4N-03) — see the `web-api` skill.
 */
public final class DTOMessageConstants {

    //@ValidUUID — the field name is prepended by the error handler
    public static final String INVALID_UUID = "must be a valid UUID";

    //Devices (P4N-03). A registration token is an opaque string from FCM; the cap is generous but
    //keeps the UNIQUE index entry well inside Postgres's btree limit
    public static final int TOKEN_MAX_LENGTH = 1024;
    public static final int LOCALE_MAX_LENGTH = 35;
    public static final int APP_VERSION_MAX_LENGTH = 50;
    // Per-user quota: abandoned installs are evicted oldest-first beyond this (SEC-07 lesson)
    public static final int MAX_DEVICES_PER_USER = 20;
    public static final String DEVICE_ID_REQUIRED = "deviceId is mandatory";
    public static final String TOKEN_REQUIRED = "token is mandatory";
    public static final String TOKEN_TOO_LONG = "token must be at most " + TOKEN_MAX_LENGTH + " characters";
    public static final String PLATFORM_REQUIRED = "platform is mandatory";
    public static final String LOCALE_TOO_LONG = "locale must be at most " + LOCALE_MAX_LENGTH + " characters";
    public static final String APP_VERSION_TOO_LONG = "appVersion must be at most " + APP_VERSION_MAX_LENGTH + " characters";

    private DTOMessageConstants() {
    }
}
