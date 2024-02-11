package de.coldtea.verborum.msdictionary.word.controller;

import de.coldtea.verborum.msdictionary.common.utils.UUIDValidator;
import de.coldtea.verborum.msdictionary.word.service.WordRequestDTO;
import de.coldtea.verborum.msdictionary.word.service.WordResponseDTO;
import de.coldtea.verborum.msdictionary.word.service.WordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static de.coldtea.verborum.msdictionary.common.constants.ResponseMessageConstants.*;

@RestController
@RequestMapping("/word")
@RequiredArgsConstructor
public class WordController {

    private final WordService wordService;

    @PostMapping("/{dictionaryId}")
    public ResponseEntity<String> createWords(@PathVariable String dictionaryId, @Valid @RequestBody List<WordRequestDTO> words){
        UUIDValidator.isValidUUID(dictionaryId, "Dictionary ID");
        for(WordRequestDTO word: words){
            UUIDValidator.isValidUUID(word.getWordId(), "Word ID");
        }

        wordService.saveWords(dictionaryId, words);
        return new ResponseEntity<>(WORD_SAVED_SUCCESSFULLY + dictionaryId, HttpStatus.CREATED);
    }

    @PutMapping("/{dictionaryId}")
    public ResponseEntity<String> updateWords(@PathVariable String dictionaryId, @RequestBody List<WordRequestDTO> words){
        wordService.saveWords(dictionaryId, words);

        return new ResponseEntity<>(WORD_UPDATED_SUCCESSFULLY + dictionaryId, HttpStatus.CREATED);
    }

    @DeleteMapping("/delete/{wordId}")
    public ResponseEntity<String> deleteWord(@PathVariable String wordId){
        wordService.deleteWords(List.of(wordId));

        return new ResponseEntity<>(WORD_DELETED_SUCCESSFULLY, HttpStatus.OK);
    }

    @DeleteMapping("/delete/bydictionaries")
    public ResponseEntity<String> deleteWordByDictionary(@RequestBody List<String> dictionaryIds){
        wordService.deleteWordsByDictionaryIds(dictionaryIds);

        return new ResponseEntity<>(WORD_DELETED_SUCCESSFULLY, HttpStatus.OK);
    }

    @GetMapping("/get/bydictionary/{dictionaryId}")
    public List<WordResponseDTO> getWordsByDictionary(@PathVariable String dictionaryId){
        return wordService.getWordsByDictionaryIds(List.of(dictionaryId));
    }

    @GetMapping("/get/bylanguagefrom/{language}")
    public List<WordResponseDTO> getWordsByLanguageFrom(@PathVariable String language){
        return wordService.getWordsByLanguageFrom(language);
    }

    @GetMapping("/get/bylanguageto/{language}")
    public List<WordResponseDTO> getWordsByLanguageTo(@PathVariable String language){
        return wordService.getWordsByLanguageTo(language);
    }

    @GetMapping("/get/byuser/{userId}")
    public List<WordResponseDTO> getWordsByUser(@PathVariable String userId){
        return wordService.getWordsByUserId(userId);
    }
}
