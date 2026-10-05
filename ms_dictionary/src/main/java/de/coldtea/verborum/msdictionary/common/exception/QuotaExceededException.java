package de.coldtea.verborum.msdictionary.common.exception;

/**
 * A create would take an owner past a per-owner total (SEC-07): too many dictionaries for one account,
 * or too many words in one dictionary. Handled as 400. Edits never trip it — only rows that are new.
 */
public class QuotaExceededException extends RuntimeException {

    public QuotaExceededException(String message) {
        super(message);
    }
}
