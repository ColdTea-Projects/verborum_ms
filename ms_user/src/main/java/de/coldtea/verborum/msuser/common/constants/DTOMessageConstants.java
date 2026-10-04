package de.coldtea.verborum.msuser.common.constants;

/**
 * Validation error messages used on request DTOs (@NotBlank, @NotNull, etc.).
 * Populated as entities and DTOs are added — see the `new-entity` / `new-endpoint` skills.
 */
public final class DTOMessageConstants {

    //@ValidUUID (P4-09) — the field name is prepended by the error handler
    public static final String INVALID_UUID = "must be a valid UUID";


    //User DTOs — validation messages
    public static final String USER_USER_ID = "userId is mandatory";
    public static final String USER_KEYCLOAK_ID = "keycloakId is mandatory";
    public static final String USER_EMAIL = "email is mandatory";
    public static final String USER_EMAIL_INVALID = "email must be a valid email address";
    // P4-13: the column is VARCHAR(255) — without the limit a longer name is a 500 from the database,
    // and since P4-13 the name is public on the marketplace and copied into ms_marketplace
    public static final int USER_DISPLAY_NAME_MAX = 255;
    public static final String USER_DISPLAY_NAME_TOO_LONG = "displayName must be at most " + USER_DISPLAY_NAME_MAX + " characters";


    //Vault DTOs — validation messages
    public static final String VAULT_ENTRY_DICTIONARY_ID = "dictionaryId is mandatory";

    private DTOMessageConstants() {
    }
}
