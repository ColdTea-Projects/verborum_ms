package de.coldtea.verborum.msmarketplace.dictionaryrating.service.impl;

import de.coldtea.verborum.msmarketplace.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msmarketplace.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msmarketplace.common.exception.SelfRatingException;
import de.coldtea.verborum.msmarketplace.common.utils.RatingScore;
import de.coldtea.verborum.msmarketplace.dictionaryimport.repository.DictionaryImportRepository;
import de.coldtea.verborum.msmarketplace.dictionaryrating.dto.RatingResponseDTO;
import de.coldtea.verborum.msmarketplace.dictionaryrating.entity.DictionaryRating;
import de.coldtea.verborum.msmarketplace.dictionaryrating.repository.DictionaryRatingRepository;
import de.coldtea.verborum.msmarketplace.dictionaryrating.service.DictionaryRatingService;
import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import de.coldtea.verborum.msmarketplace.dictionarystats.repository.DictionaryStatsRepository;
import de.coldtea.verborum.msmarketplace.publisher.service.PublisherService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static de.coldtea.verborum.msmarketplace.common.constants.ErrorMessageConstants.CANNOT_RATE_OWN_DICTIONARY;
import static de.coldtea.verborum.msmarketplace.common.constants.ErrorMessageConstants.IMPORT_BEFORE_RATING;
import static de.coldtea.verborum.msmarketplace.common.constants.ErrorMessageConstants.LISTING_WAS_NOT_FOUND_ID;
import static de.coldtea.verborum.msmarketplace.common.constants.ErrorMessageConstants.RATING_WAS_NOT_FOUND_ID;

@Service
@RequiredArgsConstructor
public class DictionaryRatingServiceImpl implements DictionaryRatingService {

    private final DictionaryRatingRepository dictionaryRatingRepository;

    private final DictionaryStatsRepository dictionaryStatsRepository;

    // "Only importers rate" is a local lookup: the import records live in this service (rule 5)
    private final DictionaryImportRepository dictionaryImportRepository;

    // The Forum gate, and whether a listing's publisher is still a member
    private final PublisherService publisherService;

    @Transactional
    @Override
    public RatingResponseDTO rateDictionary(String dictionaryId, String raterId, int stars) {
        // The Forum gate first — 403 before anything else is looked at, as for browse and import
        publisherService.requireMember(raterId);

        // Hidden, deleted, or published by a non-member: indistinguishable from absent, exactly as for
        // import. A rating can only be added while the listing is in the Forum
        DictionaryStats listing = dictionaryStatsRepository.findById(dictionaryId)
                .filter(stats -> Boolean.TRUE.equals(stats.getIsListed()))
                .filter(stats -> publisherService.isMember(stats.getUserId()))
                .orElseThrow(() -> new RecordNotFoundException(LISTING_WAS_NOT_FOUND_ID + dictionaryId));

        if (raterId.equals(listing.getUserId())) {
            throw new SelfRatingException(CANNOT_RATE_OWN_DICTIONARY);
        }

        // Decided 2026-10-05: only importers rate — ratings come from people who used the dictionary.
        // Removing it from the vault later does not revoke the right: the import record stays here
        if (dictionaryImportRepository.findByDictionaryIdAndUserId(dictionaryId, raterId).isEmpty()) {
            throw new ForbiddenOperationException(IMPORT_BEFORE_RATING);
        }

        DictionaryRating rating = dictionaryRatingRepository.findByDictionaryIdAndUserId(dictionaryId, raterId)
                .map(existing -> changeStars(existing, stars))
                .orElseGet(() -> addRating(dictionaryId, raterId, stars));

        return toResponse(rating);
    }

    @Override
    public RatingResponseDTO getMyRating(String dictionaryId, String raterId) {
        return dictionaryRatingRepository.findByDictionaryIdAndUserId(dictionaryId, raterId)
                .map(DictionaryRatingServiceImpl::toResponse)
                .orElseThrow(() -> new RecordNotFoundException(RATING_WAS_NOT_FOUND_ID + dictionaryId));
    }

    @Transactional
    @Override
    public void removeRating(String dictionaryId, String raterId) {
        dictionaryRatingRepository.findByDictionaryIdAndUserId(dictionaryId, raterId).ifPresent(rating -> {
            dictionaryRatingRepository.delete(rating);
            applyChange(dictionaryId, -1, -rating.getStars());
        });
    }

    @Transactional
    @Override
    public void deleteRatingsByUser(String keycloakId) {
        List<DictionaryRating> ratings = dictionaryRatingRepository.findByUserId(keycloakId);
        if (ratings.isEmpty()) {
            return;
        }
        // One aggregate update per rated listing — a user rates each listing at most once
        ratings.forEach(rating -> applyChange(rating.getDictionaryId(), -1, -rating.getStars()));
        dictionaryRatingRepository.deleteAllInBatch(ratings);
    }

    private DictionaryRating addRating(String dictionaryId, String raterId, int stars) {
        // A concurrent double-tap by the same user can race past the lookup: the second insert fails on
        // the UNIQUE constraint and its whole transaction rolls back, aggregates included — the client's
        // retry then takes the change path. The aggregates are never wrong
        DictionaryRating saved = dictionaryRatingRepository.saveAndFlush(DictionaryRating.builder()
                .ratingId(UUID.randomUUID().toString())
                .dictionaryId(dictionaryId)
                .userId(raterId)
                .stars((short) stars)
                .build());
        applyChange(dictionaryId, 1, stars);
        return saved;
    }

    private DictionaryRating changeStars(DictionaryRating rating, int stars) {
        int delta = stars - rating.getStars();
        if (delta == 0) {
            return rating;
        }
        rating.setStars((short) stars);
        DictionaryRating saved = dictionaryRatingRepository.saveAndFlush(rating);
        applyChange(rating.getDictionaryId(), 0, delta);
        return saved;
    }

    private void applyChange(String dictionaryId, int countDelta, int sumDelta) {
        dictionaryStatsRepository.applyRatingChange(dictionaryId, countDelta, sumDelta,
                RatingScore.PRIOR_MEAN * RatingScore.PRIOR_WEIGHT, RatingScore.PRIOR_WEIGHT);
    }

    private static RatingResponseDTO toResponse(DictionaryRating rating) {
        return RatingResponseDTO.builder()
                .dictionaryId(rating.getDictionaryId())
                .stars(rating.getStars().intValue())
                .ratedAt(rating.getUpdatedAt() != null ? rating.getUpdatedAt() : rating.getCreatedAt())
                .build();
    }
}
