package de.coldtea.verborum.msdictionary.common.constants;

public final class ErrorMessageConstants {
    public static final String DICTIONARY_WAS_NOT_FOUND_ID = "Dictionary was not found. ID: ";

    //Security (P3-05) — kept vague on purpose: the message must not reveal whether the
    //resource exists or who owns it
    public static final String NOT_THE_OWNER = "This resource does not belong to the authenticated user";
    public static final String NO_AUTHENTICATED_USER = "No authenticated user found";

}
