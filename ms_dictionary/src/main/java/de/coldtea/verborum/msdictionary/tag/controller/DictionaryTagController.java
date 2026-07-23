package de.coldtea.verborum.msdictionary.tag.controller;

import de.coldtea.verborum.msdictionary.common.response.Response;
import de.coldtea.verborum.msdictionary.tag.dto.DictionaryTagRequestDTO;
import de.coldtea.verborum.msdictionary.tag.dto.DictionaryTagResponseDTO;
import de.coldtea.verborum.msdictionary.tag.service.DictionaryTagService;
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

/**
 * Tags live on their own endpoint rather than inside the dictionary payload, so tagging does not
 * require re-sending (or racing with) the whole dictionary.
 */
@RestController
@RequestMapping("/dictionaries/{dictionaryId}/tags")
@RequiredArgsConstructor
public class DictionaryTagController {

    private final DictionaryTagService dictionaryTagService;

    @GetMapping
    public ResponseEntity<List<DictionaryTagResponseDTO>> getTagsByDictionary(@PathVariable String dictionaryId) {
        return new ResponseEntity<>(dictionaryTagService.getTagsByDictionary(dictionaryId, getCurrentUserId()), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<Response> addTag(@PathVariable String dictionaryId,
                                           @Valid @RequestBody DictionaryTagRequestDTO tag,
                                           WebRequest request) {
        DictionaryTagResponseDTO tagResponseDTO = dictionaryTagService.addTag(dictionaryId, tag, getCurrentUserId());
        return buildResponse(HttpStatus.CREATED, TAG_SAVED_SUCCESSFULLY, tagResponseDTO.getTag(), request);
    }

    @DeleteMapping("/{tag}")
    public ResponseEntity<Response> deleteTag(@PathVariable String dictionaryId,
                                              @PathVariable String tag,
                                              WebRequest request) {
        dictionaryTagService.deleteTag(dictionaryId, tag, getCurrentUserId());
        return buildResponse(HttpStatus.OK, TAG_DELETED_SUCCESSFULLY, tag, request);
    }

}
