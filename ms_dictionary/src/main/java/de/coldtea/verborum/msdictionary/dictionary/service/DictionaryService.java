package de.coldtea.verborum.msdictionary.dictionary.service;

import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryRequestDTO;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryResponseDTO;

import java.util.List;

public interface DictionaryService {
    /**
     * @param ownerId the caller's id, taken from the JWT — never from the request body (P3-05)
     */
    DictionaryResponseDTO saveDictionary(DictionaryRequestDTO dictionaryDto, String ownerId);
    void deleteDictionary(String dictionaryId, String ownerId);
    List<DictionaryResponseDTO> getDictionariesByUser(String userId);
    DictionaryResponseDTO getDictionaryById(String dictionaryId, String ownerId);

    /**
     * Batch fetch, filtered to what the caller may read: their own dictionaries and public ones
     * (P4-10). Other ids are dropped rather than refused — a 403 here would confirm that an id exists
     * (P3-08).
     */
    List<DictionaryResponseDTO> getDictionariesByIds(List<String> dictionaryIds, String ownerId);
    void deleteAllByUserId(String userId);

    /**
     * Publishes every public dictionary as one `dictionary.snapshot` event — the marketplace's
     * reconciliation backstop (rule 6, P4-03). Scheduled, not exposed over HTTP.
     */
    void publishPublicSnapshot();

    /**
     * A tag of this dictionary was added or removed (P4-12). For a public dictionary, bumps its
     * `updatedAt` and publishes `dictionary.updated` with the new tag set; for a private or unknown
     * one, does nothing. Called by the tag service after an actual change — never for a no-op.
     * No ownership check: the caller has already done it.
     */
    void publishTagChange(String dictionaryId);
}
