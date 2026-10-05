package de.coldtea.verborum.msuser.common.constants;

/**
 * Error messages used in service/exception logic.
 * Populated as entities and DTOs are added — see the `new-entity` / `new-endpoint` skills.
 */
public final class ErrorMessageConstants {

    public static final String USER_WAS_NOT_FOUND_ID = "User was not found. ID: ";
    public static final String USER_WAS_NOT_FOUND_KEYCLOAK_ID = "User was not found. Keycloak ID: ";

    //Security (P3-05) — vague on purpose: must not reveal whether the profile exists or who owns it
    // P4-14: the profile rules — accepted marketplace terms require a display name and a terms version
    public static final String DISPLAY_NAME_REQUIRED_WHILE_AGREEMENT_ACCEPTED =
            "displayName cannot be empty while the marketplace agreement is accepted; withdraw from the marketplace first";
    public static final String AGREEMENT_VERSION_REQUIRED =
            "marketplaceAgreementVersion is required to accept the marketplace agreement";
    // SEC-10: names may repeat, but not ones that read as the platform itself (see DisplayNameUtils)
    public static final String DISPLAY_NAME_RESERVED =
            "displayName contains a reserved word (such as Verborum, admin, moderator, official or support); choose another name";
    // Duplicate profile data (409). Checked before saving; the constraint handler is the backstop for a race
    public static final String PROFILE_ALREADY_EXISTS =
            "This account already has a profile; load it with GET /users/me instead of creating another";
    public static final String EMAIL_ALREADY_IN_USE = "This email is already used by another profile";
    // SEC-04: the profile e-mail is the token's, verified — never a free choice in the request body
    public static final String EMAIL_NOT_VERIFIED = "The token carries no verified email; verify the account's email and sign in again";
    public static final String EMAIL_NOT_THE_TOKENS = "email must be the signed-in account's own verified email";
    public static final String DATA_CONFLICT = "The request conflicts with existing data";
    public static final String NOT_THE_OWNER = "This resource does not belong to the authenticated user";
    // Returned instead of an unhandled exception's own message, which would leak internals
    public static final String INTERNAL_SERVER_ERROR = "Internal server error";
    public static final String NO_AUTHENTICATED_USER = "No authenticated user found";

    //OutboundEventPublisher — log message ({} placeholder)
    public static final String EVENT_PUBLISH_FAILED =
            "Failed to publish {} after commit. The write succeeded but the event never went out; "
                    + "consumers will not see it without a re-publish or a reconciliation run.";

    //KeycloakUserService — log messages ({} placeholders, not concatenated)
    public static final String KEYCLOAK_ADMIN_NOT_CONFIGURED =
            "keycloak.admin.client-secret is not set - the Keycloak identity for {} was NOT deleted. "
                    + "Set KEYCLOAK_ADMIN_CLIENT_SECRET to enable account deletion.";
    public static final String KEYCLOAK_USER_DELETE_FAILED =
            "Failed to delete the Keycloak identity {} (status {}). The profile and its data are gone; "
                    + "this account must be removed manually.";

    private ErrorMessageConstants() {
    }
}
