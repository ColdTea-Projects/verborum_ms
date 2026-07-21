package de.coldtea.verborum.msuser.common.constants;

/**
 * Success response messages used in controllers.
 * Populated as entities and DTOs are added — see the `new-entity` / `new-endpoint` skills.
 */
public final class ResponseMessageConstants {

    //UserController — trailing space is required: buildResponse concatenates message + detail
    public static final String USER_SAVED_SUCCESSFULLY = "Saved successfully user ";
    public static final String USER_UPDATED_SUCCESSFULLY = "Updated successfully user ";
    public static final String USER_DELETED_SUCCESSFULLY = "Deleted successfully ";

    private ResponseMessageConstants() {
    }
}
