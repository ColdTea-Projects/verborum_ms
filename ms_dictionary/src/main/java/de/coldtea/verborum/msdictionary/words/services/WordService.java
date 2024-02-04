package de.coldtea.verborum.msdictionary.words.services;

import de.coldtea.verborum.msdictionary.common.utils.ListUtils;
import de.coldtea.verborum.msdictionary.dictionaries.repository.Dictionary;
import de.coldtea.verborum.msdictionary.dictionaries.repository.DictionaryRepository;
import de.coldtea.verborum.msdictionary.words.repository.Word;
import de.coldtea.verborum.msdictionary.words.repository.WordRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class WordService {

    private final WordRepository wordRepository;

    private final DictionaryRepository dictionaryRepository;

    private final ListUtils listUtils = new ListUtils();

    public WordService(WordRepository wordRepository, DictionaryRepository dictionaryRepository) {
        this.wordRepository = wordRepository;
        this.dictionaryRepository = dictionaryRepository;
    }

    @Transactional
    public void saveWords(String dictionaryId, List<WordDTO> wordList){
        List<Word> words = wordList.stream()
                .map(wordDTO -> new Word(
                        wordDTO.getWordId(),
                        dictionaryId,
                        wordDTO.getWord(),
                        wordDTO.getWordMeta(),
                        wordDTO.getTranslation(),
                        wordDTO.getTranslationMeta()
                )).collect(Collectors.toList());

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

}
