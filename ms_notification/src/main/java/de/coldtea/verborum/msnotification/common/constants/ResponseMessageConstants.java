package de.coldtea.verborum.msnotification.common.constants;

/**
 * Success response messages used in controllers.
 * Populated as endpoints are added (P4N-03) — see the `web-api` skill. Keep the trailing space on each:
 * buildResponse concatenates message + detail.
 */
public final class ResponseMessageConstants {

    //DeviceTokenController
    public static final String DEVICE_REGISTERED_SUCCESSFULLY = "Registered successfully device ";
    public static final String DEVICE_REMOVED_SUCCESSFULLY = "Removed device ";

    private ResponseMessageConstants() {
    }
}
