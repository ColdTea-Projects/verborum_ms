package de.coldtea.verborum.msdictionary.word.service;

import de.coldtea.verborum.msdictionary.word.dto.WordBundleRequestDTO;
import de.coldtea.verborum.msdictionary.word.dto.WordRequestDTO;
import de.coldtea.verborum.msdictionary.word.dto.WordResponseDTO;
import de.coldtea.verborum.msdictionary.word.entity.Word;

import java.util.List;
import java.util.stream.Stream;

public interface WordService {

    /**
     * @param ownerId the caller's id from the JWT; every target dictionary must belong to it (P3-05)
     */
    void saveWords(List<WordBundleRequestDTO> wordList, String ownerId);

    void deleteWords(List<String> wordIdList, String ownerId);

    void deleteWordsByDictionaryId(String dictionaryId, String ownerId);

    /** Scoped to the caller's own dictionaries — these used to return every user's words (P3-08). */
    List<WordResponseDTO> getWordsByLanguageFrom(String language, String ownerId);

    List<WordResponseDTO> getWordsByLanguageTo(String language, String ownerId);

    List<WordResponseDTO> getWordsByUserId(String userId);

    List<WordResponseDTO> getWordsByDictionaryIds(List<String> dictionaryIds, String ownerId);

    List<WordResponseDTO> getWordsByIds(List<String> wordIds, String ownerId);
}
