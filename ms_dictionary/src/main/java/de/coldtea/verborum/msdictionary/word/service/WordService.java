package de.coldtea.verborum.msdictionary.word.service;

import de.coldtea.verborum.msdictionary.common.utils.ListUtils;
import de.coldtea.verborum.msdictionary.dictionary.repository.Dictionary;
import de.coldtea.verborum.msdictionary.dictionary.repository.DictionaryRepository;
import de.coldtea.verborum.msdictionary.word.repository.Word;
import de.coldtea.verborum.msdictionary.word.repository.WordRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WordService {

    private final WordRepository wordRepository;

    private final DictionaryRepository dictionaryRepository;

    private final ListUtils listUtils = new ListUtils();

    @Transactional
    public void saveWords(String dictionaryId, List<WordRequestDTO> wordList){
        List<Word> words = listUtils.map(wordList,
                wordRequestDTO -> convertToWord(dictionaryId, wordRequestDTO)
        );

        wordRepository.saveAllAndFlush(words);
    }

    @Transactional
    public void deleteWords(List<String> wordIdList){
        wordRepository.deleteAllById(wordIdList);
    }

    @Transactional
    public void deleteWordsByDictionaryIds(List<String> dictionaryId){
        wordRepository.deleteByDictionaryIds(dictionaryId);
    }

    public List<WordResponseDTO> getWordsByLanguageFrom(String language){
        List<String> dictionaryIds = listUtils.map(dictionaryRepository.getByLanguageFrom(language), Dictionary::getDictionaryId);
        List<Word> words = wordRepository.getByDictionaryIds(dictionaryIds);

        return listUtils.map(words, this::convertToDTO);
    }

    public List<WordResponseDTO> getWordsByLanguageTo(String language){
        List<String> dictionaryIds = listUtils.map(dictionaryRepository.getByLanguageTo(language), Dictionary::getDictionaryId);
        List<Word> words = wordRepository.getByDictionaryIds(dictionaryIds);

        return listUtils.map(words, this::convertToDTO);
    }

    public List<WordResponseDTO> getWordsByUserId(String userId){
        List<String> dictionaryIds = listUtils.map(dictionaryRepository.getByUserId(userId), Dictionary::getDictionaryId);
        List<Word> words = wordRepository.getByDictionaryIds(dictionaryIds);

        return listUtils.map(words, this::convertToDTO);
    }

    public List<WordResponseDTO> getWordsByDictionaryIds(List<String> dictionaryIds){
        List<Word> words = wordRepository.getByDictionaryIds(dictionaryIds);

        return listUtils.map(words, this::convertToDTO);
    }

    public Word getWord(String wordId){
        return wordRepository.findById(wordId).orElseThrow();
    }

    private Word convertToWord(String dictionaryId, WordRequestDTO wordRequestDTO){
        return new Word(
                wordRequestDTO.getWordId(),
                dictionaryId,
                wordRequestDTO.getWord(),
                wordRequestDTO.getWordMeta(),
                wordRequestDTO.getTranslation(),
                wordRequestDTO.getTranslationMeta()
        );
    }

    private WordResponseDTO convertToDTO(Word word){
        return new WordResponseDTO(
                word.getWordId(),
                word.getDictionaryId(),
                word.getWord(),
                word.getWordMeta(),
                word.getTranslation(),
                word.getTranslationMeta()
        );
    }

}
