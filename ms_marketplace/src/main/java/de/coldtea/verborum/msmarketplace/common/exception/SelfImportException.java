package de.coldtea.verborum.msmarketplace.common.exception;

import java.io.Serial;

/**
 * A publisher tried to import their own dictionary (P4-07). Handled as 400: the client is meant to
 * prevent it, so reaching the server is a client bug — and allowing it would let a publisher boost
 * their own dictionary's popularity.
 */
public class SelfImportException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 4471932108563302291L;

    public SelfImportException(String message) {
        super(message);
    }
}
