package de.coldtea.verborum.msdictionary.word.service.impl;

import de.coldtea.verborum.msdictionary.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msdictionary.common.mapper.WordMapper;
import de.coldtea.verborum.msdictionary.dictionary.entity.Dictionary;
import de.coldtea.verborum.msdictionary.dictionary.repository.DictionaryRepository;
import de.coldtea.verborum.msdictionary.word.entity.Word;
import de.coldtea.verborum.msdictionary.word.repository.WordRepository;
import de.coldtea.verborum.msdictionary.word.dto.WordRequestDTO;
import de.coldtea.verborum.msdictionary.word.dto.WordResponseDTO;
import de.coldtea.verborum.msdictionary.word.service.WordService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static de.coldtea.verborum.msdictionary.common.constants.ErrorMessageConstants.DICTIONARY_WAS_NOT_FOUND_ID;

@Service
@RequiredArgsConstructor
public class WordServiceImpl implements WordService {

    private final WordRepository wordRepository;

    private final DictionaryRepository dictionaryRepository;
    private final WordMapper wordMapper;

    @Transactional
    @Override
    public void saveWords(String dictionaryId, List<WordRequestDTO> wordList) {
        dictionaryRepository.findById(dictionaryId)
                .orElseThrow(() -> new RecordNotFoundException(DICTIONARY_WAS_NOT_FOUND_ID + dictionaryId));

        wordRepository.saveAllAndFlush(wordList.stream().map(wordMapper::toWord).toList());
    }

    @Transactional
    @Override
    public void deleteWords(List<String> wordIdList) {
        wordRepository.deleteAllById(wordIdList);
    }

    @Override
    @Transactional
    public void deleteWordsByDictionaryId(String dictionaryId) {
        dictionaryRepository.findById(dictionaryId)
                .orElseThrow(() -> new RecordNotFoundException(DICTIONARY_WAS_NOT_FOUND_ID + dictionaryId));

        wordRepository.deleteWordsByDictionaryId(dictionaryId);
    }

    @Override
    public List<WordResponseDTO> getWordsByLanguageFrom(String language) {
        List<String> dictIds = dictionaryRepository.findByFromLang(language).stream().map(Dictionary::getDictionaryId).toList();
        List<Word> words = wordRepository.findByDictionaryIdIn(dictIds);
        return words.stream().map(wordMapper::toWordResponseDTO).toList();
    }

    @Override
    public List<WordResponseDTO> getWordsByLanguageTo(String language) {
        List<String> dictIds = dictionaryRepository.findByToLang(language).stream().map(Dictionary::getDictionaryId).toList();
        List<Word> words = wordRepository.findByDictionaryIdIn(dictIds);
        return words.stream().map(wordMapper::toWordResponseDTO).toList();
    }

    @Override
    public List<WordResponseDTO> getWordsByUserId(String userId) {
        List<String> dictIds = dictionaryRepository.findByUserId(userId).stream().map(Dictionary::getDictionaryId).toList();
        List<Word> words = wordRepository.findByDictionaryIdIn(dictIds);

        return words.stream().map(wordMapper::toWordResponseDTO).toList();
    }

    @Override
    public List<WordResponseDTO> getWordsByDictionaryIds(List<String> dictionaryIds) {
        return wordRepository.findByDictionaryIdIn(dictionaryIds).stream().map(wordMapper::toWordResponseDTO).toList();
    }
}
