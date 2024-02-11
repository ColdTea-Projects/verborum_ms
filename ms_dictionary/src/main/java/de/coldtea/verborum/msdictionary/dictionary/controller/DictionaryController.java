package de.coldtea.verborum.msdictionary.dictionary.controller;

import de.coldtea.verborum.msdictionary.common.response.SuccessResponse;
import de.coldtea.verborum.msdictionary.common.utils.LanguageCodeValidator;
import de.coldtea.verborum.msdictionary.common.utils.UUIDValidator;
import de.coldtea.verborum.msdictionary.dictionary.service.DictionaryService;
import de.coldtea.verborum.msdictionary.dictionary.service.dto.DictionaryRequestDTO;
import de.coldtea.verborum.msdictionary.dictionary.service.dto.DictionaryResponseDTO;
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
    public ResponseEntity<SuccessResponse> createDictionary(@Valid @RequestBody DictionaryRequestDTO dictionary, WebRequest request) {
        UUIDValidator.isValidUUID(dictionary.getDictionaryId(), "Dictionary ID");
        UUIDValidator.isValidUUID(dictionary.getUserId(), "User ID");
        LanguageCodeValidator.isValidLanguageCode(dictionary.getFromLang());
        LanguageCodeValidator.isValidLanguageCode(dictionary.getToLang());

        dictionaryService.saveDictionary(dictionary);
        return new ResponseEntity<>(SuccessResponse.builder()
                .status(HttpStatus.CREATED.value())
                .message(DICTIONARY_SAVED_SUCCESSFULLY + dictionary.getDictionaryId())
                .path(request.getContextPath())
                .timestamp(OffsetDateTime.now())
                .build(), HttpStatus.CREATED);
    }

    @PutMapping("/")
    public ResponseEntity<SuccessResponse> updateDictionary(@Valid @RequestBody DictionaryRequestDTO dictionary, WebRequest request) {
        UUIDValidator.isValidUUID(dictionary.getDictionaryId(), "Dictionary ID");
        UUIDValidator.isValidUUID(dictionary.getUserId(), "User ID");
        LanguageCodeValidator.isValidLanguageCode(dictionary.getFromLang());
        LanguageCodeValidator.isValidLanguageCode(dictionary.getToLang());

        dictionaryService.saveDictionary(dictionary);
        return new ResponseEntity<>(SuccessResponse.builder()
                .status(HttpStatus.CREATED.value())
                .message(DICTIONARY_UPDATED_SUCCESSFULLY + dictionary.getDictionaryId())
                .path(request.getContextPath())
                .timestamp(OffsetDateTime.now())
                .build(), HttpStatus.CREATED);
    }

    @DeleteMapping("/{dictionaryId}")
    public ResponseEntity<SuccessResponse> deleteDictionary(@PathVariable String dictionaryId, WebRequest request) {
        UUIDValidator.isValidUUID(dictionaryId, "Dictionary ID");
        dictionaryService.deleteDictionary(dictionaryId);
        return new ResponseEntity<>(SuccessResponse.builder()
                .status(HttpStatus.OK.value())
                .message(DICTIONARY_DELETED_SUCCESSFULLY + dictionaryId)
                .path(request.getContextPath())
                .timestamp(OffsetDateTime.now())
                .build(), HttpStatus.OK);
    }

    @GetMapping("/get/bydictionaryids")
    public List<DictionaryResponseDTO> getAllDictionaries(@RequestBody List<String> dictionaryIds) {
        for (String dictionaryId : dictionaryIds) {
            UUIDValidator.isValidUUID(dictionaryId, "Dictionary ID");
        }
        return dictionaryService.getDictionariesById(dictionaryIds);
    }

    @GetMapping("/get/byuserid/{userId}")
    public List<DictionaryResponseDTO> getAllDictionaries(@PathVariable String userId) {
        UUIDValidator.isValidUUID(userId, "User ID");
        return dictionaryService.getDictionariesByUser(userId);
    }

    @GetMapping("/get/all")
    public List<DictionaryResponseDTO> getAllDictionaries() {
        return dictionaryService.getDictionaries();
    }
}
