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

@RestController
@RequestMapping("/dictionaries")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryService dictionaryService;

    @PostMapping("/")
    public ResponseEntity<Response> createDictionary(@Valid @RequestBody DictionaryRequestDTO dictionary, WebRequest request) {

        DictionaryResponseDTO dictionaryResponseDTO = dictionaryService.saveDictionary(dictionary);
        return buildResponse(HttpStatus.CREATED, DICTIONARY_SAVED_SUCCESSFULLY, dictionaryResponseDTO.getDictionaryId(), request);
    }

    @PutMapping("/")
    public ResponseEntity<Response> updateDictionary(@Valid @RequestBody DictionaryRequestDTO dictionary, WebRequest request) {

        dictionaryService.saveDictionary(dictionary);
        return buildResponse(HttpStatus.CREATED, DICTIONARY_UPDATED_SUCCESSFULLY, dictionary.getDictionaryId(), request);
    }

    @DeleteMapping("/{dictionaryId}")
    public ResponseEntity<Response> deleteDictionary(@PathVariable String dictionaryId, WebRequest request) {
        dictionaryService.deleteDictionary(dictionaryId);
        return buildResponse(HttpStatus.OK, DICTIONARY_DELETED_SUCCESSFULLY, dictionaryId, request);
    }


    @GetMapping("/{userId}")
    public ResponseEntity<List<DictionaryResponseDTO>> getAllDictionariesByUser(@PathVariable String userId) {
        return new ResponseEntity<>(dictionaryService.getDictionariesByUser(userId), HttpStatus.OK);
    }


}
