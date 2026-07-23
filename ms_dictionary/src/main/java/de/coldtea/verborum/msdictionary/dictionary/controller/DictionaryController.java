package de.coldtea.verborum.msdictionary.dictionary.controller;

import de.coldtea.verborum.msdictionary.common.response.Response;
import de.coldtea.verborum.msdictionary.dictionary.service.DictionaryService;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryRequestDTO;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

import static de.coldtea.verborum.msdictionary.common.constants.ResponseMessageConstants.*;
import static de.coldtea.verborum.msdictionary.common.utils.ResponseUtils.buildResponse;
import static de.coldtea.verborum.msdictionary.common.utils.SecurityUtils.getCurrentUserId;
import static de.coldtea.verborum.msdictionary.common.utils.SecurityUtils.requireSelf;

@RestController
@RequestMapping("/dictionaries")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryService dictionaryService;

    @PostMapping("/")
    public ResponseEntity<Response> createDictionary(@Valid @RequestBody DictionaryRequestDTO dictionary, WebRequest request) {

        DictionaryResponseDTO dictionaryResponseDTO = dictionaryService.saveDictionary(dictionary, getCurrentUserId());
        return buildResponse(HttpStatus.CREATED, DICTIONARY_SAVED_SUCCESSFULLY, dictionaryResponseDTO.getDictionaryId(), request);
    }

    @PutMapping("/")
    public ResponseEntity<Response> updateDictionary(@Valid @RequestBody DictionaryRequestDTO dictionary, WebRequest request) {

        dictionaryService.saveDictionary(dictionary, getCurrentUserId());
        return buildResponse(HttpStatus.CREATED, DICTIONARY_UPDATED_SUCCESSFULLY, dictionary.getDictionaryId(), request);
    }

    @DeleteMapping("/{dictionaryId}")
    public ResponseEntity<Response> deleteDictionary(@PathVariable String dictionaryId, WebRequest request) {
        dictionaryService.deleteDictionary(dictionaryId);
        return buildResponse(HttpStatus.OK, DICTIONARY_DELETED_SUCCESSFULLY, dictionaryId, request);
    }


    /**
     * The path variable is kept for backward compatibility with existing clients, but it must name
     * the caller — a token cannot list another user's dictionaries (P3-05).
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<DictionaryResponseDTO>> getAllDictionariesByUser(@PathVariable String userId) {
        requireSelf(userId);
        return new ResponseEntity<>(dictionaryService.getDictionariesByUser(userId), HttpStatus.OK);
    }

    @GetMapping("/dictionary/{dictionaryId}")
    public ResponseEntity<DictionaryResponseDTO> getDictionaryById(@PathVariable String dictionaryId) {
        return new ResponseEntity<>(dictionaryService.getDictionaryById(dictionaryId), HttpStatus.OK);
    }

    @GetMapping("/batch")
    public ResponseEntity<List<DictionaryResponseDTO>> getDictionariesByIds(@RequestParam List<String> ids) {
        return new ResponseEntity<>(dictionaryService.getDictionariesByIds(ids), HttpStatus.OK);
    }

}
