package de.coldtea.verborum.msdictionary.words.controller;

import de.coldtea.verborum.msdictionary.dictionaries.repository.Dictionary;
import de.coldtea.verborum.msdictionary.words.repository.Word;
import de.coldtea.verborum.msdictionary.words.services.WordDTO;
import de.coldtea.verborum.msdictionary.words.services.WordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/word")
@RequiredArgsConstructor
public class WordController {

    WordService wordService;

    @PostMapping("/{dictionaryId}")
    public ResponseEntity<String> createWords(@PathVariable String dictionaryId, @RequestBody List<WordDTO> words){
        wordService.saveWords(dictionaryId, words);

        return new ResponseEntity<>("Saved successfully into dictionary " + dictionaryId, HttpStatus.CREATED);
    }

    @PutMapping("/{dictionaryId}")
    public ResponseEntity<String> updateWords(@PathVariable String dictionaryId, @RequestBody List<WordDTO> words){
        wordService.saveWords(dictionaryId, words);

        return new ResponseEntity<>("Saved successfully into dictionary " + dictionaryId, HttpStatus.CREATED);
    }

    @DeleteMapping("/delete/{wordId}")
    public ResponseEntity<String> deleteWord(@PathVariable String wordId){
        wordService.deleteWords(List.of(wordId));

        return new ResponseEntity<>(wordId + " deleted successfully", HttpStatus.OK);
    }

    @DeleteMapping("/delete/bydictionaries")
    public ResponseEntity<String> deleteWordByDictionary(@RequestBody List<String> dictionaryIds){
        wordService.deleteWordsByDictionaryIds(dictionaryIds);

        return new ResponseEntity<>("Deleted successfully", HttpStatus.OK);
    }

    @GetMapping("/get/bydictionary/{dictionaryId}")
    public List<Word> getWordsByDictionary(@PathVariable String dictionaryId){
        return wordService.getWordsByDictionaryIds(List.of(dictionaryId));
    }

    @GetMapping("/get/bylanguagefrom/{language}")
    public List<Word> getWordsByLanguageFrom(@PathVariable String language){
        return wordService.getWordsByLanguageFrom(language);
    }

    @GetMapping("/get/bylanguageto/{language}")
    public List<Word> getWordsByLanguageTo(@PathVariable String language){
        return wordService.getWordsByLanguageTo(language);
    }

    @GetMapping("/get/byuser/{userId}")
    public List<Word> getWordsByUser(@PathVariable String userId){
        return wordService.getWordsByUserId(userId);
    }
}
