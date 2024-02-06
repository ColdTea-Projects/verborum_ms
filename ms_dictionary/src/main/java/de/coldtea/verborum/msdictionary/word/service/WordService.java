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
    public void saveWords(String dictionaryId, List<WordDTO> wordList){
        List<Word> words = listUtils.map(wordList,
                wordDTO -> convertToWord(dictionaryId, wordDTO)
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

    public List<Word> getWordsByLanguageFrom(String language){
        List<String> dictionaryIds = listUtils.map(dictionaryRepository.getByLanguageFrom(language), Dictionary::getDictionaryId);

        return wordRepository.getByDictionaryIds(dictionaryIds);
    }

    public List<Word> getWordsByLanguageTo(String language){
        List<String> dictionaryIds = listUtils.map(dictionaryRepository.getByLanguageTo(language), Dictionary::getDictionaryId);

        return wordRepository.getByDictionaryIds(dictionaryIds);
    }

    public List<Word> getWordsByUserId(String userId){
        List<String> dictionaryIds = listUtils.map(dictionaryRepository.getByUserId(userId), Dictionary::getDictionaryId);

        return wordRepository.getByDictionaryIds(dictionaryIds);
    }

    public List<Word> getWordsByDictionaryIds(List<String> dictionaryIds){
        return wordRepository.getByDictionaryIds(dictionaryIds);
    }

    public Word getWord(String wordId){
        return wordRepository.findById(wordId).orElseThrow();
    }

    private Word convertToWord(String dictionaryId, WordDTO wordDTO){
        return new Word(
                wordDTO.getWordId(),
                dictionaryId,
                wordDTO.getWord(),
                wordDTO.getWordMeta(),
                wordDTO.getTranslation(),
                wordDTO.getTranslationMeta()
        );
    }

}
