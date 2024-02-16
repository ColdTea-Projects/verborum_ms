package de.coldtea.verborum.msdictionary.word.service;

import de.coldtea.verborum.msdictionary.word.dto.WordRequestDTO;
import de.coldtea.verborum.msdictionary.word.dto.WordResponseDTO;

import java.util.List;

public interface WordService {

    void saveWords(String dictionaryId, List<WordRequestDTO> wordList);

    void deleteWords(List<String> wordIdList);

    void deleteWordsByDictionaryId(String dictionaryId);
    List<WordResponseDTO> getWordsByLanguageFrom(String language);

    List<WordResponseDTO> getWordsByLanguageTo(String language);

    List<WordResponseDTO> getWordsByUserId(String userId);

    List<WordResponseDTO> getWordsByDictionaryIds(List<String> dictionaryIds);
}
