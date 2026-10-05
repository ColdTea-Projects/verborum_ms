package de.coldtea.verborum.msmarketplace.dictionarystats.controller;

import de.coldtea.verborum.msmarketplace.common.response.Response;
import de.coldtea.verborum.msmarketplace.common.response.SliceResponse;
import de.coldtea.verborum.msmarketplace.common.utils.LanguagePair;
import de.coldtea.verborum.msmarketplace.dictionaryimport.service.DictionaryImportService;
import de.coldtea.verborum.msmarketplace.dictionaryrating.dto.RatingRequestDTO;
import de.coldtea.verborum.msmarketplace.dictionaryrating.dto.RatingResponseDTO;
import de.coldtea.verborum.msmarketplace.dictionaryrating.service.DictionaryRatingService;
import de.coldtea.verborum.msmarketplace.dictionarystats.dto.DictionaryListingResponseDTO;
import de.coldtea.verborum.msmarketplace.dictionarystats.dto.ListingFilter;
import de.coldtea.verborum.msmarketplace.dictionarystats.service.DictionaryStatsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.LANGUAGE_PAIRS_MAX;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.PAGE_DEFAULT;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.PAGE_NEGATIVE;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.PAGE_SIZE_DEFAULT;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.PAGE_SIZE_MAX;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.PAGE_SIZE_OUT_OF_RANGE;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.PUBLISHER_NAME_LENGTH;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.PUBLISHER_NAME_MAX;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.PUBLISHER_NAME_MIN;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.TAGS_MAX;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.TAG_BLANK;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.TAG_MAX_LENGTH;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.TAG_TOO_LONG;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.TOO_MANY_LANGUAGE_PAIRS;
import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.TOO_MANY_TAGS;
import static de.coldtea.verborum.msmarketplace.common.constants.ResponseMessageConstants.DICTIONARY_IMPORTED_SUCCESSFULLY;
import static de.coldtea.verborum.msmarketplace.common.constants.ResponseMessageConstants.DICTIONARY_RATED_SUCCESSFULLY;
import static de.coldtea.verborum.msmarketplace.common.constants.ResponseMessageConstants.RATING_REMOVED_SUCCESSFULLY;
import static de.coldtea.verborum.msmarketplace.common.utils.ResponseUtils.buildResponse;
import static de.coldtea.verborum.msmarketplace.common.utils.SecurityUtils.getCurrentUserId;

/**
 * Marketplace browse (P4-06). Read-only and served entirely from the local read model — never a call
 * to ms_dictionary (rule 5). Browse and import are for marketplace members only (the Forum gate: a
 * display name and accepted terms, else 403); every listed dictionary is public, so there is no
 * ownership filter. Import (P4-07) is the one write.
 * <p>
 * Parameter constraints (@Min/@Max, @Size, @NotBlank, @LanguagePair — on list elements too) are
 * enforced by Spring MVC's built-in method validation — no class-level @Validated, which would switch
 * to the AOP variant and a different exception. Failures are 400s via GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/marketplace/dictionaries")
@RequiredArgsConstructor
public class MarketplaceController {

    private final DictionaryStatsService dictionaryStatsService;

    private final DictionaryImportService dictionaryImportService;

    private final DictionaryRatingService dictionaryRatingService;

    /**
     * Import a listed dictionary into the caller's vault (P4-07). The importer is the token subject,
     * never a request field. 404 for an unknown, private or deleted listing; 400 for your own.
     * Idempotent — a repeat import is 201 again and changes no counts.
     */
    @PostMapping("/{dictionaryId}/import")
    public ResponseEntity<Response> importDictionary(@PathVariable String dictionaryId, WebRequest request) {
        dictionaryImportService.importDictionary(dictionaryId, getCurrentUserId());
        return buildResponse(HttpStatus.CREATED, DICTIONARY_IMPORTED_SUCCESSFULLY, dictionaryId, request);
    }

    /**
     * Newest first. `pair` narrows to the given language pairs in both directions — `pair=EN-TR`
     * returns EN→TR and TR→EN listings (P4-11). `tag` narrows to listings with any of the given tags,
     * in any case (P4-12). Both repeat for several values (`pair=EN-TR&pair=FR-DE`). `publisher` is
     * part of a publisher's display name, any case — "nna" finds "Anna Bauer" (P4-13). Leave a filter
     * out to not filter on it. Listings of publishers without a display name are never returned.
     */
    @GetMapping
    public ResponseEntity<SliceResponse<DictionaryListingResponseDTO>> getListings(
            @RequestParam(required = false)
            @Size(max = LANGUAGE_PAIRS_MAX, message = TOO_MANY_LANGUAGE_PAIRS) List<@LanguagePair String> pair,
            @RequestParam(required = false)
            @Size(max = TAGS_MAX, message = TOO_MANY_TAGS)
            List<@NotBlank(message = TAG_BLANK) @Size(max = TAG_MAX_LENGTH, message = TAG_TOO_LONG) String> tag,
            @RequestParam(required = false)
            @Size(min = PUBLISHER_NAME_MIN, max = PUBLISHER_NAME_MAX, message = PUBLISHER_NAME_LENGTH) String publisher,
            @RequestParam(defaultValue = PAGE_DEFAULT) @Min(value = 0, message = PAGE_NEGATIVE) int page,
            @RequestParam(defaultValue = PAGE_SIZE_DEFAULT)
            @Min(value = 1, message = PAGE_SIZE_OUT_OF_RANGE) @Max(value = PAGE_SIZE_MAX, message = PAGE_SIZE_OUT_OF_RANGE) int size) {
        return new ResponseEntity<>(dictionaryStatsService.getListings(new ListingFilter(pair, tag, publisher), page, size, getCurrentUserId()), HttpStatus.OK);
    }

    /** Most imported first, with the same filters as {@link #getListings}. */
    @GetMapping("/popular")
    public ResponseEntity<SliceResponse<DictionaryListingResponseDTO>> getPopularListings(
            @RequestParam(required = false)
            @Size(max = LANGUAGE_PAIRS_MAX, message = TOO_MANY_LANGUAGE_PAIRS) List<@LanguagePair String> pair,
            @RequestParam(required = false)
            @Size(max = TAGS_MAX, message = TOO_MANY_TAGS)
            List<@NotBlank(message = TAG_BLANK) @Size(max = TAG_MAX_LENGTH, message = TAG_TOO_LONG) String> tag,
            @RequestParam(required = false)
            @Size(min = PUBLISHER_NAME_MIN, max = PUBLISHER_NAME_MAX, message = PUBLISHER_NAME_LENGTH) String publisher,
            @RequestParam(defaultValue = PAGE_DEFAULT) @Min(value = 0, message = PAGE_NEGATIVE) int page,
            @RequestParam(defaultValue = PAGE_SIZE_DEFAULT)
            @Min(value = 1, message = PAGE_SIZE_OUT_OF_RANGE) @Max(value = PAGE_SIZE_MAX, message = PAGE_SIZE_OUT_OF_RANGE) int size) {
        return new ResponseEntity<>(dictionaryStatsService.getPopularListings(new ListingFilter(pair, tag, publisher), page, size, getCurrentUserId()), HttpStatus.OK);
    }

    /**
     * Best rated first (P4-21), with the same filters as {@link #getListings}. Ranked by a Bayesian
     * average, so a listing needs several good ratings to rise; only listings someone has rated.
     */
    @GetMapping("/top-rated")
    public ResponseEntity<SliceResponse<DictionaryListingResponseDTO>> getTopRatedListings(
            @RequestParam(required = false)
            @Size(max = LANGUAGE_PAIRS_MAX, message = TOO_MANY_LANGUAGE_PAIRS) List<@LanguagePair String> pair,
            @RequestParam(required = false)
            @Size(max = TAGS_MAX, message = TOO_MANY_TAGS)
            List<@NotBlank(message = TAG_BLANK) @Size(max = TAG_MAX_LENGTH, message = TAG_TOO_LONG) String> tag,
            @RequestParam(required = false)
            @Size(min = PUBLISHER_NAME_MIN, max = PUBLISHER_NAME_MAX, message = PUBLISHER_NAME_LENGTH) String publisher,
            @RequestParam(defaultValue = PAGE_DEFAULT) @Min(value = 0, message = PAGE_NEGATIVE) int page,
            @RequestParam(defaultValue = PAGE_SIZE_DEFAULT)
            @Min(value = 1, message = PAGE_SIZE_OUT_OF_RANGE) @Max(value = PAGE_SIZE_MAX, message = PAGE_SIZE_OUT_OF_RANGE) int size) {
        return new ResponseEntity<>(dictionaryStatsService.getTopRatedListings(new ListingFilter(pair, tag, publisher), page, size, getCurrentUserId()), HttpStatus.OK);
    }

    /**
     * Rate a listing 1–5 (P4-20), or change the caller's rating. Members only (403); listed (404); not
     * your own (400); only after importing it (403). The rater is the token subject. Idempotent — 201.
     */
    @PutMapping("/{dictionaryId}/rating")
    public ResponseEntity<Response> rateDictionary(@PathVariable String dictionaryId,
                                                   @Valid @RequestBody RatingRequestDTO rating, WebRequest request) {
        dictionaryRatingService.rateDictionary(dictionaryId, getCurrentUserId(), rating.getStars());
        return buildResponse(HttpStatus.CREATED, DICTIONARY_RATED_SUCCESSFULLY, dictionaryId, request);
    }

    /** The caller's own rating of a listing (P4-20); 404 when they have not rated it. */
    @GetMapping("/{dictionaryId}/rating")
    public ResponseEntity<RatingResponseDTO> getMyRating(@PathVariable String dictionaryId) {
        return new ResponseEntity<>(dictionaryRatingService.getMyRating(dictionaryId, getCurrentUserId()), HttpStatus.OK);
    }

    /** Withdraw the caller's rating (P4-20); 200 also when there was none. */
    @DeleteMapping("/{dictionaryId}/rating")
    public ResponseEntity<Response> removeRating(@PathVariable String dictionaryId, WebRequest request) {
        dictionaryRatingService.removeRating(dictionaryId, getCurrentUserId());
        return buildResponse(HttpStatus.OK, RATING_REMOVED_SUCCESSFULLY, dictionaryId, request);
    }

    /**
     * "More from this publisher" — `publisherId` is the value on every listing. An unknown id is an
     * empty page, not a 404: it is a filter, like any list endpoint.
     */
    @GetMapping("/publisher/{publisherId}")
    public ResponseEntity<SliceResponse<DictionaryListingResponseDTO>> getListingsByPublisher(
            @PathVariable String publisherId,
            @RequestParam(defaultValue = PAGE_DEFAULT) @Min(value = 0, message = PAGE_NEGATIVE) int page,
            @RequestParam(defaultValue = PAGE_SIZE_DEFAULT)
            @Min(value = 1, message = PAGE_SIZE_OUT_OF_RANGE) @Max(value = PAGE_SIZE_MAX, message = PAGE_SIZE_OUT_OF_RANGE) int size) {
        return new ResponseEntity<>(dictionaryStatsService.getListingsByPublisher(publisherId, page, size, getCurrentUserId()), HttpStatus.OK);
    }
}
