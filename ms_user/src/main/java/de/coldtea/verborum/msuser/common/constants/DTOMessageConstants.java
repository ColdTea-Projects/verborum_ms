package de.coldtea.verborum.msuser.common.constants;

/**
 * Validation error messages used on request DTOs (@NotBlank, @NotNull, etc.).
 * Populated as entities and DTOs are added — see the `new-entity` / `new-endpoint` skills.
 */
public final class DTOMessageConstants {

    //User DTOs — ValidUUID field names
    public static final String USER_ID = "userId";
    public static final String KEYCLOAK_ID = "keycloakId";

    //User DTOs — validation messages
    public static final String USER_USER_ID = "userId is mandatory";
    public static final String USER_KEYCLOAK_ID = "keycloakId is mandatory";
    public static final String USER_EMAIL = "email is mandatory";
    public static final String USER_EMAIL_INVALID = "email must be a valid email address";

    //Vault DTOs — ValidUUID field names
    public static final String DICTIONARY_ID = "dictionaryId";

    //Vault DTOs — validation messages
    public static final String VAULT_ENTRY_DICTIONARY_ID = "dictionaryId is mandatory";

    private DTOMessageConstants() {
    }
}
