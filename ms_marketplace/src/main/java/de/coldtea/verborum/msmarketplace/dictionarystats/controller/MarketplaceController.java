package de.coldtea.verborum.msmarketplace.dictionarystats.controller;

import de.coldtea.verborum.msmarketplace.common.response.PageResponse;
import de.coldtea.verborum.msmarketplace.common.utils.SupportedLanguage;
import de.coldtea.verborum.msmarketplace.dictionarystats.dto.DictionaryListingResponseDTO;
import de.coldtea.verborum.msmarketplace.dictionarystats.service.DictionaryStatsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.PAGE_DEFAULT;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.PAGE_NEGATIVE;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.PAGE_SIZE_DEFAULT;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.PAGE_SIZE_MAX;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.PAGE_SIZE_OUT_OF_RANGE;

/**
 * Marketplace browse (P4-06). Read-only and served entirely from the local read model — never a call
 * to ms_dictionary (rule 5). Any authenticated caller may browse: every listed dictionary is public,
 * so there is no ownership filter. The import endpoint arrives with its event at P4-07.
 * <p>
 * Parameter constraints (@Min/@Max, @SupportedLanguage) are enforced by Spring MVC's built-in method
 * validation — no class-level @Validated, which would switch to the AOP variant and a different
 * exception. Failures are 400s via GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/marketplace/dictionaries")
@RequiredArgsConstructor
public class MarketplaceController {

    private final DictionaryStatsService dictionaryStatsService;

    @GetMapping
    public ResponseEntity<PageResponse<DictionaryListingResponseDTO>> getListings(
            @RequestParam(defaultValue = PAGE_DEFAULT) @Min(value = 0, message = PAGE_NEGATIVE) int page,
            @RequestParam(defaultValue = PAGE_SIZE_DEFAULT)
            @Min(value = 1, message = PAGE_SIZE_OUT_OF_RANGE) @Max(value = PAGE_SIZE_MAX, message = PAGE_SIZE_OUT_OF_RANGE) int size) {
        return new ResponseEntity<>(dictionaryStatsService.getListings(page, size), HttpStatus.OK);
    }

    @GetMapping("/popular")
    public ResponseEntity<PageResponse<DictionaryListingResponseDTO>> getPopularListings(
            @RequestParam(defaultValue = PAGE_DEFAULT) @Min(value = 0, message = PAGE_NEGATIVE) int page,
            @RequestParam(defaultValue = PAGE_SIZE_DEFAULT)
            @Min(value = 1, message = PAGE_SIZE_OUT_OF_RANGE) @Max(value = PAGE_SIZE_MAX, message = PAGE_SIZE_OUT_OF_RANGE) int size) {
        return new ResponseEntity<>(dictionaryStatsService.getPopularListings(page, size), HttpStatus.OK);
    }

    @GetMapping("/language")
    public ResponseEntity<PageResponse<DictionaryListingResponseDTO>> getListingsByLanguage(
            @RequestParam @SupportedLanguage String from,
            @RequestParam @SupportedLanguage String to,
            @RequestParam(defaultValue = PAGE_DEFAULT) @Min(value = 0, message = PAGE_NEGATIVE) int page,
            @RequestParam(defaultValue = PAGE_SIZE_DEFAULT)
            @Min(value = 1, message = PAGE_SIZE_OUT_OF_RANGE) @Max(value = PAGE_SIZE_MAX, message = PAGE_SIZE_OUT_OF_RANGE) int size) {
        return new ResponseEntity<>(dictionaryStatsService.getListingsByLanguage(from, to, page, size), HttpStatus.OK);
    }

    /**
     * "More from this publisher" — `publisherId` is the value on every listing. An unknown id is an
     * empty page, not a 404: it is a filter, like any list endpoint.
     */
    @GetMapping("/publisher/{publisherId}")
    public ResponseEntity<PageResponse<DictionaryListingResponseDTO>> getListingsByPublisher(
            @PathVariable String publisherId,
            @RequestParam(defaultValue = PAGE_DEFAULT) @Min(value = 0, message = PAGE_NEGATIVE) int page,
            @RequestParam(defaultValue = PAGE_SIZE_DEFAULT)
            @Min(value = 1, message = PAGE_SIZE_OUT_OF_RANGE) @Max(value = PAGE_SIZE_MAX, message = PAGE_SIZE_OUT_OF_RANGE) int size) {
        return new ResponseEntity<>(dictionaryStatsService.getListingsByPublisher(publisherId, page, size), HttpStatus.OK);
    }
}
