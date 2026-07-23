package de.coldtea.verborum.msdictionary.dictionary.service;

import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryRequestDTO;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryResponseDTO;

import java.util.List;

public interface DictionaryService {
    /**
     * @param ownerId the caller's id, taken from the JWT — never from the request body (P3-05)
     */
    DictionaryResponseDTO saveDictionary(DictionaryRequestDTO dictionaryDto, String ownerId);
    void deleteDictionary(String dictionaryId);
    List<DictionaryResponseDTO> getDictionariesByUser(String userId);
    DictionaryResponseDTO getDictionaryById(String dictionaryId);
    List<DictionaryResponseDTO> getDictionariesByIds(List<String> dictionaryIds);
    void deleteAllByUserId(String userId);
}
