package de.coldtea.verborum.msmarketplace.dictionaryrating.service.impl;

import de.coldtea.verborum.msmarketplace.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msmarketplace.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msmarketplace.common.exception.SelfRatingException;
import de.coldtea.verborum.msmarketplace.dictionaryimport.entity.DictionaryImport;
import de.coldtea.verborum.msmarketplace.dictionaryimport.repository.DictionaryImportRepository;
import de.coldtea.verborum.msmarketplace.dictionaryrating.dto.RatingResponseDTO;
import de.coldtea.verborum.msmarketplace.dictionaryrating.entity.DictionaryRating;
import de.coldtea.verborum.msmarketplace.dictionaryrating.repository.DictionaryRatingRepository;
import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import de.coldtea.verborum.msmarketplace.dictionarystats.repository.DictionaryStatsRepository;
import de.coldtea.verborum.msmarketplace.publisher.service.PublisherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DictionaryRatingServiceImplTest {

    private static final String DICTIONARY_ID = "dict1";
    private static final String PUBLISHER = "kc-publisher";
    private static final String RATER = "kc-rater";

    // m·C and C from RatingScore — the values every aggregate update must carry
    private static final double PRIOR_TOTAL = 15.0;
    private static final int PRIOR_WEIGHT = 5;

    @Mock
    private DictionaryRatingRepository dictionaryRatingRepository;

    @Mock
    private DictionaryStatsRepository dictionaryStatsRepository;

    @Mock
    private DictionaryImportRepository dictionaryImportRepository;

    @Mock
    private PublisherService publisherService;

    @InjectMocks
    private DictionaryRatingServiceImpl dictionaryRatingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // A listed listing by a member, imported by a member who has not rated it yet
        when(publisherService.isMember(PUBLISHER)).thenReturn(true);
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(listing(true)));
        when(dictionaryImportRepository.findByDictionaryIdAndUserId(DICTIONARY_ID, RATER))
                .thenReturn(Optional.of(new DictionaryImport()));
        when(dictionaryRatingRepository.findByDictionaryIdAndUserId(DICTIONARY_ID, RATER)).thenReturn(Optional.empty());
        when(dictionaryRatingRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static DictionaryStats listing(boolean listed) {
        return DictionaryStats.builder().dictionaryId(DICTIONARY_ID).userId(PUBLISHER).isListed(listed).build();
    }

    private static DictionaryRating rating(int stars) {
        return DictionaryRating.builder().ratingId("r1").dictionaryId(DICTIONARY_ID).userId(RATER).stars((short) stars).build();
    }

    @Test
    void rateDictionary_FirstRating_SavesItAndAddsOneRatingToTheAggregates() {
        // Act
        RatingResponseDTO result = dictionaryRatingService.rateDictionary(DICTIONARY_ID, RATER, 4);

        // Assert
        ArgumentCaptor<DictionaryRating> captor = ArgumentCaptor.forClass(DictionaryRating.class);
        verify(dictionaryRatingRepository).saveAndFlush(captor.capture());
        assertEquals(RATER, captor.getValue().getUserId());
        assertEquals((short) 4, captor.getValue().getStars());
        verify(dictionaryStatsRepository).applyRatingChange(DICTIONARY_ID, 1, 4, PRIOR_TOTAL, PRIOR_WEIGHT);
        assertEquals(4, result.getStars());
    }

    @Test
    void rateDictionary_ChangedStars_UpdatesTheRatingAndOnlyTheSum() {
        // Arrange
        when(dictionaryRatingRepository.findByDictionaryIdAndUserId(DICTIONARY_ID, RATER)).thenReturn(Optional.of(rating(2)));

        // Act
        dictionaryRatingService.rateDictionary(DICTIONARY_ID, RATER, 5);

        // Assert — still one rating, three stars more
        verify(dictionaryStatsRepository).applyRatingChange(DICTIONARY_ID, 0, 3, PRIOR_TOTAL, PRIOR_WEIGHT);
    }

    @Test
    void rateDictionary_SameStarsAgain_ChangesNothing() {
        // Arrange
        when(dictionaryRatingRepository.findByDictionaryIdAndUserId(DICTIONARY_ID, RATER)).thenReturn(Optional.of(rating(4)));

        // Act
        dictionaryRatingService.rateDictionary(DICTIONARY_ID, RATER, 4);

        // Assert
        verify(dictionaryRatingRepository, never()).saveAndFlush(any());
        verify(dictionaryStatsRepository, never()).applyRatingChange(anyString(), anyInt(), anyInt(), anyDouble(), anyInt());
    }

    @Test
    void rateDictionary_NotAMember_Is403BeforeAnythingIsLookedAt() {
        // Arrange — the Forum gate
        doThrow(new ForbiddenOperationException("join first")).when(publisherService).requireMember(RATER);

        // Act & Assert
        assertThrows(ForbiddenOperationException.class, () -> dictionaryRatingService.rateDictionary(DICTIONARY_ID, RATER, 4));
        verifyNoInteractions(dictionaryStatsRepository, dictionaryRatingRepository);
    }

    @Test
    void rateDictionary_HiddenListing_Is404() {
        // Arrange — gone private: indistinguishable from absent
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(listing(false)));

        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> dictionaryRatingService.rateDictionary(DICTIONARY_ID, RATER, 4));
        verifyNoInteractions(dictionaryRatingRepository);
    }

    @Test
    void rateDictionary_PublisherLeftTheForum_Is404() {
        // Arrange
        when(publisherService.isMember(PUBLISHER)).thenReturn(false);

        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> dictionaryRatingService.rateDictionary(DICTIONARY_ID, RATER, 4));
        verifyNoInteractions(dictionaryRatingRepository);
    }

    @Test
    void rateDictionary_OwnDictionary_Is400() {
        // Act & Assert
        assertThrows(SelfRatingException.class, () -> dictionaryRatingService.rateDictionary(DICTIONARY_ID, PUBLISHER, 5));
        verifyNoInteractions(dictionaryRatingRepository);
    }

    @Test
    void rateDictionary_NotImported_Is403() {
        // Arrange — decided 2026-10-05: only importers rate
        when(dictionaryImportRepository.findByDictionaryIdAndUserId(DICTIONARY_ID, RATER)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ForbiddenOperationException.class, () -> dictionaryRatingService.rateDictionary(DICTIONARY_ID, RATER, 4));
        verifyNoInteractions(dictionaryRatingRepository);
        verify(dictionaryStatsRepository, never()).applyRatingChange(anyString(), anyInt(), anyInt(), anyDouble(), anyInt());
    }

    @Test
    void getMyRating_Rated_ReturnsTheStars() {
        // Arrange
        when(dictionaryRatingRepository.findByDictionaryIdAndUserId(DICTIONARY_ID, RATER)).thenReturn(Optional.of(rating(3)));

        // Act & Assert
        assertEquals(3, dictionaryRatingService.getMyRating(DICTIONARY_ID, RATER).getStars());
    }

    @Test
    void getMyRating_NotRated_Is404() {
        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> dictionaryRatingService.getMyRating(DICTIONARY_ID, RATER));
    }

    @Test
    void removeRating_Rated_DeletesItAndTakesItOutOfTheAggregates() {
        // Arrange
        DictionaryRating existing = rating(5);
        when(dictionaryRatingRepository.findByDictionaryIdAndUserId(DICTIONARY_ID, RATER)).thenReturn(Optional.of(existing));

        // Act
        dictionaryRatingService.removeRating(DICTIONARY_ID, RATER);

        // Assert
        verify(dictionaryRatingRepository).delete(existing);
        verify(dictionaryStatsRepository).applyRatingChange(DICTIONARY_ID, -1, -5, PRIOR_TOTAL, PRIOR_WEIGHT);
    }

    @Test
    void removeRating_NotRated_IsANoOp() {
        // Act
        dictionaryRatingService.removeRating(DICTIONARY_ID, RATER);

        // Assert
        verify(dictionaryRatingRepository, never()).delete(any());
        verify(dictionaryStatsRepository, never()).applyRatingChange(anyString(), anyInt(), anyInt(), anyDouble(), anyInt());
    }

    @Test
    void deleteRatingsByUser_CorrectsEveryRatedListingThenDeletes() {
        // Arrange
        DictionaryRating first = rating(4);
        DictionaryRating second = DictionaryRating.builder().ratingId("r2").dictionaryId("dict2").userId(RATER).stars((short) 1).build();
        when(dictionaryRatingRepository.findByUserId(RATER)).thenReturn(List.of(first, second));

        // Act
        dictionaryRatingService.deleteRatingsByUser(RATER);

        // Assert
        verify(dictionaryStatsRepository).applyRatingChange(DICTIONARY_ID, -1, -4, PRIOR_TOTAL, PRIOR_WEIGHT);
        verify(dictionaryStatsRepository).applyRatingChange("dict2", -1, -1, PRIOR_TOTAL, PRIOR_WEIGHT);
        verify(dictionaryRatingRepository).deleteAllInBatch(List.of(first, second));
    }

    @Test
    void deleteRatingsByUser_NoRatings_IsANoOp() {
        // Arrange — a redelivered user.deleted
        when(dictionaryRatingRepository.findByUserId(RATER)).thenReturn(List.of());

        // Act
        dictionaryRatingService.deleteRatingsByUser(RATER);

        // Assert
        verify(dictionaryRatingRepository, never()).deleteAllInBatch(any());
        verify(dictionaryStatsRepository, never()).applyRatingChange(anyString(), anyInt(), anyInt(), anyDouble(), anyInt());
    }
}
