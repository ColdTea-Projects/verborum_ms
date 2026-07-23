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

    void deleteWords(List<String> wordIdList);

    void deleteWordsByDictionaryId(String dictionaryId);
    List<WordResponseDTO> getWordsByLanguageFrom(String language);

    List<WordResponseDTO> getWordsByLanguageTo(String language);

    List<WordResponseDTO> getWordsByUserId(String userId);

    List<WordResponseDTO> getWordsByDictionaryIds(List<String> dictionaryIds);

    List<WordResponseDTO> getWordsByIds(List<String> wordIds);
}
