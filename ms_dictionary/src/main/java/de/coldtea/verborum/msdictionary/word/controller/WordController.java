package de.coldtea.verborum.msdictionary.word.controller;

import de.coldtea.verborum.msdictionary.common.response.Response;
import de.coldtea.verborum.msdictionary.word.service.dto.WordRequestDTO;
import de.coldtea.verborum.msdictionary.word.service.dto.WordResponseDTO;
import de.coldtea.verborum.msdictionary.word.service.WordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.time.OffsetDateTime;
import java.util.List;

import static de.coldtea.verborum.msdictionary.common.constants.ResponseMessageConstants.*;

@RestController
@RequestMapping("/word")
@RequiredArgsConstructor
public class WordController {

    private final WordService wordService;

    @PostMapping("/{dictionaryId}")
    public ResponseEntity<Response> createWords(@PathVariable String dictionaryId, @Valid @RequestBody List<WordRequestDTO> words, WebRequest request){
        UUIDValidator.isValidUUID(dictionaryId, "Dictionary ID");
        for(WordRequestDTO word: words){
            UUIDValidator.isValidUUID(word.getWordId(), "Word ID");
        }

        wordService.saveWords(dictionaryId, words);
        return new ResponseEntity<>(Response.builder()
                .status(HttpStatus.CREATED.value())
                .message(WORD_SAVED_SUCCESSFULLY + dictionaryId)
                .path(request.getContextPath())
                .timestamp(OffsetDateTime.now())
                .build(), HttpStatus.CREATED);
    }

    @PutMapping("/{dictionaryId}")
    public ResponseEntity<Response> updateWords(@PathVariable String dictionaryId, @RequestBody List<WordRequestDTO> words, WebRequest request){
        UUIDValidator.isValidUUID(dictionaryId, "Dictionary ID");
        for(WordRequestDTO word: words){
            UUIDValidator.isValidUUID(word.getWordId(), "Word ID");
        }
        wordService.saveWords(dictionaryId, words);

        return new ResponseEntity<>(Response.builder()
                .status(HttpStatus.CREATED.value())
                .message(WORD_UPDATED_SUCCESSFULLY + dictionaryId)
                .path(request.getContextPath())
                .timestamp(OffsetDateTime.now())
                .build(), HttpStatus.CREATED);
    }

    @DeleteMapping("/delete/{wordId}")
    public ResponseEntity<Response> deleteWord(@PathVariable String wordId, WebRequest request){
        UUIDValidator.isValidUUID(wordId, "Word ID");
        wordService.deleteWords(List.of(wordId));

        return new ResponseEntity<>(Response.builder()
                .status(HttpStatus.OK.value())
                .message(WORD_DELETED_SUCCESSFULLY)
                .path(request.getContextPath())
                .timestamp(OffsetDateTime.now())
                .build(), HttpStatus.OK);
    }

    @DeleteMapping("/delete/bydictionaries")
    public ResponseEntity<Response> deleteWordByDictionary(@RequestBody List<String> dictionaryIds, WebRequest request){
        for(String dictionaryId: dictionaryIds){
            UUIDValidator.isValidUUID(dictionaryId, "Dictionary ID");
        }
        wordService.deleteWordsByDictionaryIds(dictionaryIds);

        return new ResponseEntity<>(Response.builder()
                .status(HttpStatus.OK.value())
                .message(WORD_DELETED_SUCCESSFULLY)
                .path(request.getContextPath())
                .timestamp(OffsetDateTime.now())
                .build(), HttpStatus.OK);
    }

    @GetMapping("/get/bydictionary/{dictionaryId}")
    public List<WordResponseDTO> getWordsByDictionary(@PathVariable String dictionaryId, WebRequest request){
        UUIDValidator.isValidUUID(dictionaryId, "Dictionary ID");
        return wordService.getWordsByDictionaryIds(List.of(dictionaryId));
    }

    @GetMapping("/get/bylanguagefrom/{language}")
    public List<WordResponseDTO> getWordsByLanguageFrom(@PathVariable String language, WebRequest request){
        LanguageCodeValidator.isValidLanguageCode(language);
        return wordService.getWordsByLanguageFrom(language);
    }

    @GetMapping("/get/bylanguageto/{language}")
    public List<WordResponseDTO> getWordsByLanguageTo(@PathVariable String language, WebRequest request){
        LanguageCodeValidator.isValidLanguageCode(language);
        return wordService.getWordsByLanguageTo(language);
    }

    @GetMapping("/get/byuser/{userId}")
    public List<WordResponseDTO> getWordsByUser(@PathVariable String userId, WebRequest request){
        return wordService.getWordsByUserId(userId);
    }
}
