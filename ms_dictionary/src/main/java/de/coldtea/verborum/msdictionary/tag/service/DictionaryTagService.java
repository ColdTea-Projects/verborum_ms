package de.coldtea.verborum.msdictionary.tag.service;

import de.coldtea.verborum.msdictionary.tag.dto.DictionaryTagRequestDTO;
import de.coldtea.verborum.msdictionary.tag.dto.DictionaryTagResponseDTO;

import java.util.List;

/**
 * Tags on a dictionary. `ownerId` is the caller's JWT subject — tags follow their dictionary's
 * ownership rules exactly (P3-05/P3-08): writes on someone else's dictionary are 403, reads are 404.
 */
public interface DictionaryTagService {
    List<DictionaryTagResponseDTO> getTagsByDictionary(String dictionaryId, String ownerId);
    DictionaryTagResponseDTO addTag(String dictionaryId, DictionaryTagRequestDTO tagRequestDTO, String ownerId);
    void deleteTag(String dictionaryId, String tag, String ownerId);
}
