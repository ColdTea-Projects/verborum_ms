package de.coldtea.verborum.msdictionary.dictionary.service;

import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryRequestDTO;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryResponseDTO;

import java.util.List;

public interface DictionaryService {
    DictionaryResponseDTO saveDictionary(DictionaryRequestDTO dictionaryDto);
    void deleteDictionary(String dictionaryId);
    List<DictionaryResponseDTO> getDictionariesByUser(String userId);
}
