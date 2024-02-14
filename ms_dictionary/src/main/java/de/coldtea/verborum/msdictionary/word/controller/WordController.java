package de.coldtea.verborum.msdictionary.word.controller;

import de.coldtea.verborum.msdictionary.common.response.Response;
import de.coldtea.verborum.msdictionary.common.utils.ResponseUtils;
import de.coldtea.verborum.msdictionary.common.utils.SupportedLanguage;
import de.coldtea.verborum.msdictionary.common.utils.ValidUUID;
import de.coldtea.verborum.msdictionary.word.dto.WordRequestDTO;
import de.coldtea.verborum.msdictionary.word.dto.WordResponseDTO;
import de.coldtea.verborum.msdictionary.word.service.WordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

import static de.coldtea.verborum.msdictionary.common.constants.ResponseMessageConstants.*;
import static de.coldtea.verborum.msdictionary.common.utils.ResponseUtils.getListOfWords;

@RestController
@RequestMapping("/words")
@RequiredArgsConstructor
public class WordController {

    private final WordService wordService;


    @PostMapping("/{dictionaryId}")
    public ResponseEntity<Response> createWords(@PathVariable @ValidUUID(fieldName = "dictionaryId") String dictionaryId,
                                                @Valid @RequestBody List<WordRequestDTO> words, WebRequest request) {

        wordService.saveWords(dictionaryId, words);

        String listOfWords = getListOfWords(words);

        return ResponseUtils.buildResponse(HttpStatus.CREATED, WORD_SAVED_SUCCESSFULLY, listOfWords, request);
    }

    @PutMapping("/{dictionaryId}")
    public ResponseEntity<Response> updateWords(@PathVariable @ValidUUID(fieldName = "dictionaryId") String dictionaryId, @RequestBody List<WordRequestDTO> words, WebRequest request) {

        wordService.saveWords(dictionaryId, words);

        String listOfWords = getListOfWords(words);

        return ResponseUtils.buildResponse(HttpStatus.CREATED, WORD_UPDATED_SUCCESSFULLY, listOfWords, request);
    }

    @DeleteMapping("/{wordId}")
    public ResponseEntity<Response> deleteWord(@PathVariable @ValidUUID(fieldName = "wordId") String wordId, WebRequest request) {
        wordService.deleteWords(List.of(wordId));

        return ResponseUtils.buildResponse(HttpStatus.OK, WORD_DELETED_SUCCESSFULLY, wordId, request);
    }

    @DeleteMapping("/dictionary/{dictionaryId}")
    public ResponseEntity<Response> deleteWordsByDictionary(@PathVariable @ValidUUID(fieldName = "dictionaryId") String dictionaryId, WebRequest request) {

        wordService.deleteWordsByDictionaryId(dictionaryId);

        return ResponseUtils.buildResponse(HttpStatus.OK, WORD_DELETED_SUCCESSFULLY_BY_DICT_ID, dictionaryId, request);
    }

    @GetMapping("/dictionary/{dictionaryId}")
    public List<WordResponseDTO> getWordsByDictionary(@PathVariable String dictionaryId) {
        return wordService.getWordsByDictionaryIds(List.of(dictionaryId));
    }

    @GetMapping("/language/from/{language}")
    public List<WordResponseDTO> getWordsByLanguageFrom(@PathVariable @SupportedLanguage String language) {
        return wordService.getWordsByLanguageFrom(language);
    }

    @GetMapping("/language/to/{language}")
    public List<WordResponseDTO> getWordsByLanguageTo(@PathVariable String language) {
        return wordService.getWordsByLanguageTo(language);
    }

    @GetMapping("/user/{userId}")
    public List<WordResponseDTO> getWordsByUser(@PathVariable String userId) {
        return wordService.getWordsByUserId(userId);
    }
}
