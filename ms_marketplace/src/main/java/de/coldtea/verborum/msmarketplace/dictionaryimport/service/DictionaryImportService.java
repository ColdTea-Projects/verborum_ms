package de.coldtea.verborum.msmarketplace.dictionaryimport.service;

public interface DictionaryImportService {

    /**
     * Imports a listed dictionary into the caller's vault (P4-07): records the import, counts it once
     * per user, and publishes `dictionary.imported` for ms_user after commit.
     * <p>
     * Idempotent: a repeat import changes nothing here and re-sends the event (ms_user's vault insert
     * is idempotent too, and the re-send repairs a vault entry whose first event was lost).
     *
     * @param importerId the caller's id, taken from the JWT — never from the request (P3-05)
     * @throws de.coldtea.verborum.msmarketplace.common.exception.RecordNotFoundException unknown or
     *         hidden listing (a private or deleted dictionary is not importable) — 404
     * @throws de.coldtea.verborum.msmarketplace.common.exception.SelfImportException the caller is
     *         the publisher — 400
     */
    void importDictionary(String dictionaryId, String importerId);
}
