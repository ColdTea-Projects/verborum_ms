package de.coldtea.verborum.msdictionary.word.controller;

import de.coldtea.verborum.msdictionary.common.response.Response;
import de.coldtea.verborum.msdictionary.common.utils.ResponseUtils;
import de.coldtea.verborum.msdictionary.common.utils.SupportedLanguage;
import de.coldtea.verborum.msdictionary.common.utils.ValidUUID;
import de.coldtea.verborum.msdictionary.word.dto.WordBundleRequestDTO;
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
import static de.coldtea.verborum.msdictionary.common.utils.ResponseUtils.getDictionaryIds;
import static de.coldtea.verborum.msdictionary.common.utils.SecurityUtils.getCurrentUserId;
import static de.coldtea.verborum.msdictionary.common.utils.SecurityUtils.requireSelf;

@RestController
@RequestMapping("/words")
@RequiredArgsConstructor
public class WordController {

    private final WordService wordService;

    @PostMapping("")
    public ResponseEntity<Response> createWords(@Valid @RequestBody List<WordBundleRequestDTO> bundles, WebRequest request) {
        wordService.saveWords(bundles, getCurrentUserId());

        return ResponseUtils.buildResponse(HttpStatus.CREATED, WORD_SAVED_SUCCESSFULLY, getDictionaryIds(bundles), request);
    }

    @PutMapping("")
    public ResponseEntity<Response> updateWords(@Valid @RequestBody List<WordBundleRequestDTO> bundles, WebRequest request) {
        wordService.saveWords(bundles, getCurrentUserId());

        return ResponseUtils.buildResponse(HttpStatus.CREATED, WORD_UPDATED_SUCCESSFULLY, getDictionaryIds(bundles), request);
    }

    @DeleteMapping("/{wordId}")
    public ResponseEntity<Response> deleteWord(@PathVariable @ValidUUID String wordId, WebRequest request) {
        wordService.deleteWords(List.of(wordId), getCurrentUserId());

        return ResponseUtils.buildResponse(HttpStatus.OK, WORD_DELETED_SUCCESSFULLY, wordId, request);
    }

    @DeleteMapping("/dictionary/{dictionaryId}")
    public ResponseEntity<Response> deleteWordsByDictionary(@PathVariable @ValidUUID String dictionaryId, WebRequest request) {

        wordService.deleteWordsByDictionaryId(dictionaryId, getCurrentUserId());

        return ResponseUtils.buildResponse(HttpStatus.OK, WORD_DELETED_SUCCESSFULLY_BY_DICT_ID, dictionaryId, request);
    }

    @GetMapping("/dictionary/{dictionaryId}")
    public List<WordResponseDTO> getWordsByDictionary(@PathVariable String dictionaryId) {
        return wordService.getWordsByDictionaryIds(List.of(dictionaryId), getCurrentUserId());
    }

    @GetMapping("/language/from/{language}")
    public List<WordResponseDTO> getWordsByLanguageFrom(@PathVariable @SupportedLanguage String language) {
        return wordService.getWordsByLanguageFrom(language, getCurrentUserId());
    }

    @GetMapping("/language/to/{language}")
    public List<WordResponseDTO> getWordsByLanguageTo(@PathVariable @SupportedLanguage String language) {
        return wordService.getWordsByLanguageTo(language, getCurrentUserId());
    }

    /**
     * The path variable is kept for backward compatibility, but it must name the caller — a token
     * cannot read another user's words (P3-05).
     */
    @GetMapping("/user/{userId}")
    public List<WordResponseDTO> getWordsByUser(@PathVariable String userId) {
        requireSelf(userId);
        return wordService.getWordsByUserId(userId);
    }

    @GetMapping("/batch")
    public List<WordResponseDTO> getWordsByIds(@RequestParam List<String> ids) {
        return wordService.getWordsByIds(ids, getCurrentUserId());
    }
}
