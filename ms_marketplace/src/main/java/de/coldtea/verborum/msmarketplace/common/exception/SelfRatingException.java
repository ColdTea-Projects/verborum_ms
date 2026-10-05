package de.coldtea.verborum.msmarketplace.common.exception;

/**
 * A publisher tried to rate their own dictionary (P4-20). Handled as 400, like SelfImportException —
 * a publisher cannot import their own dictionary either, so they could never pass the importer rule.
 */
public class SelfRatingException extends RuntimeException {

    public SelfRatingException(String message) {
        super(message);
    }
}
