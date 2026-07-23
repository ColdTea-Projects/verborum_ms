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
     * Batch fetch, filtered to the caller's own dictionaries. Ids belonging to someone else are
     * dropped rather than refused — a 403 here would confirm that an id exists (P3-08).
     */
    List<DictionaryResponseDTO> getDictionariesByIds(List<String> dictionaryIds, String ownerId);
    void deleteAllByUserId(String userId);
}
