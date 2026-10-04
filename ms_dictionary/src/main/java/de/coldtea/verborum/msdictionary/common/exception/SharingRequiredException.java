package de.coldtea.verborum.msdictionary.common.exception;

/**
 * A change would leave a marketplace member with dictionaries but none of them shared (P4-16): making
 * the last shared one private, deleting it while private ones remain, or creating a private one while
 * none is shared. Handled as 400. Leaving the marketplace makes all of them private instead.
 */
public class SharingRequiredException extends RuntimeException {

    public SharingRequiredException(String message) {
        super(message);
    }
}
