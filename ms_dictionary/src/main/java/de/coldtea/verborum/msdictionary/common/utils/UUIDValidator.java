package de.coldtea.verborum.msdictionary.common.utils;

import de.coldtea.verborum.msdictionary.common.exception.InvalidUUIDException;

import java.util.UUID;

public class UUIDValidator {

    public static boolean isValidUUID(String uuidString, String fieldName) {
        try {
            UUID.fromString(uuidString);
            // If no exception is thrown, the UUID is valid
            return true;
        } catch (IllegalArgumentException e) {
            // If an exception is thrown, the UUID is not valid
            throw new InvalidUUIDException(fieldName);
        }
    }
}