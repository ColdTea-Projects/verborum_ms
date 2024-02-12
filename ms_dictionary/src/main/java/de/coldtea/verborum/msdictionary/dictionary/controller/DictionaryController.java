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

import java.time.OffsetDateTime;
import java.util.List;

import static de.coldtea.verborum.msdictionary.common.constants.ResponseMessageConstants.*;

@RestController
@RequestMapping("/dictionary")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryService dictionaryService;

    @PostMapping("/")
    public ResponseEntity<Response> createDictionary(@Valid @RequestBody DictionaryRequestDTO dictionary, WebRequest request) {

        dictionaryService.saveDictionary(dictionary);
        return new ResponseEntity<>(Response.builder()
                .status(HttpStatus.CREATED.value())
                .message(DICTIONARY_SAVED_SUCCESSFULLY + dictionary.getDictionaryId())
                .path(request.getContextPath())
                .timestamp(OffsetDateTime.now())
                .build(), HttpStatus.CREATED);
    }

    @PutMapping("/")
    public ResponseEntity<Response> updateDictionary(@Valid @RequestBody DictionaryRequestDTO dictionary, WebRequest request) {

        dictionaryService.saveDictionary(dictionary);
        return new ResponseEntity<>(Response.builder()
                .status(HttpStatus.CREATED.value())
                .message(DICTIONARY_UPDATED_SUCCESSFULLY + dictionary.getDictionaryId())
                .path(request.getContextPath())
                .timestamp(OffsetDateTime.now())
                .build(), HttpStatus.CREATED);
    }

    @DeleteMapping("/{dictionaryId}")
    public ResponseEntity<Response> deleteDictionary(@PathVariable String dictionaryId, WebRequest request) {
        dictionaryService.deleteDictionary(dictionaryId);
        return new ResponseEntity<>(Response.builder()
                .status(HttpStatus.OK.value())
                .message(DICTIONARY_DELETED_SUCCESSFULLY + dictionaryId)
                .path(request.getContextPath())
                .timestamp(OffsetDateTime.now())
                .build(), HttpStatus.OK);
    }


    @GetMapping("/{userId}")
    public List<DictionaryResponseDTO> getAllDictionaries(@PathVariable String userId) {
        return dictionaryService.getDictionariesByUser(userId);
    }

}
