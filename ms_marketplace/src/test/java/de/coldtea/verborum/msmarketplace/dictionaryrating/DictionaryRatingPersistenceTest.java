package de.coldtea.verborum.msmarketplace.dictionaryrating;

import de.coldtea.verborum.msmarketplace.dictionaryimport.entity.DictionaryImport;
import de.coldtea.verborum.msmarketplace.dictionaryimport.repository.DictionaryImportRepository;
import de.coldtea.verborum.msmarketplace.dictionaryrating.repository.DictionaryRatingRepository;
import de.coldtea.verborum.msmarketplace.dictionaryrating.service.DictionaryRatingService;
import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import de.coldtea.verborum.msmarketplace.dictionarystats.repository.DictionaryStatsRepository;
import de.coldtea.verborum.msmarketplace.publisher.entity.Publisher;
import de.coldtea.verborum.msmarketplace.publisher.repository.PublisherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rating writes against the real database (needs the local stack, like the context test). Unit tests
 * mock the repositories and cannot see the persistence context: a rating delete left unflushed was
 * discarded by the aggregate UPDATE's clearAutomatically — the count dropped while the row stayed. This
 * pins the row and the aggregates together after every write. Rolled back at the end.
 */
@SpringBootTest
@Transactional
class DictionaryRatingPersistenceTest {

    @Autowired
    private DictionaryRatingService dictionaryRatingService;

    @Autowired
    private DictionaryRatingRepository dictionaryRatingRepository;

    @Autowired
    private DictionaryStatsRepository dictionaryStatsRepository;

    @Autowired
    private DictionaryImportRepository dictionaryImportRepository;

    @Autowired
    private PublisherRepository publisherRepository;

    @Test
    void rateChangeRemove_RowAndAggregatesStayInStep() {
        // Arrange — a listed dictionary by a member, imported by another member
        String publisher = UUID.randomUUID().toString();
        String rater = UUID.randomUUID().toString();
        String dictionaryId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        for (String member : new String[] {publisher, rater}) {
            publisherRepository.saveAndFlush(Publisher.builder().keycloakId(member).displayName("Member " + member.substring(0, 4))
                    .marketplaceAgreementAccepted(true).sourceUpdatedAt(now).build());
        }
        dictionaryStatsRepository.saveAndFlush(DictionaryStats.builder().dictionaryId(dictionaryId).userId(publisher)
                .name("Test").fromLang("EN").toLang("DE").langPair("DE-EN").tags(new String[0]).isListed(true)
                .importCount(1).ratingCount(0).ratingSum(0).ratingScore(3.0)
                .publishedAt(now).sourceUpdatedAt(now).build());
        dictionaryImportRepository.saveAndFlush(DictionaryImport.builder().importId(UUID.randomUUID().toString())
                .dictionaryId(dictionaryId).userId(rater).build());

        // Act & Assert — rate
        dictionaryRatingService.rateDictionary(dictionaryId, rater, 5);
        assertAggregates(dictionaryId, 1, 5, (5 + 15) / 6.0);
        assertTrue(dictionaryRatingRepository.findByDictionaryIdAndUserId(dictionaryId, rater).isPresent());

        // change
        dictionaryRatingService.rateDictionary(dictionaryId, rater, 2);
        assertAggregates(dictionaryId, 1, 2, (2 + 15) / 6.0);
        assertEquals(2, dictionaryRatingRepository.findByDictionaryIdAndUserId(dictionaryId, rater).orElseThrow().getStars().intValue());

        // remove — the case that lost the delete
        dictionaryRatingService.removeRating(dictionaryId, rater);
        assertAggregates(dictionaryId, 0, 0, 3.0);
        assertTrue(dictionaryRatingRepository.findByDictionaryIdAndUserId(dictionaryId, rater).isEmpty());
    }

    private void assertAggregates(String dictionaryId, int count, int sum, double score) {
        DictionaryStats stats = dictionaryStatsRepository.findById(dictionaryId).orElseThrow();
        assertEquals(count, stats.getRatingCount());
        assertEquals(sum, stats.getRatingSum());
        assertEquals(score, stats.getRatingScore(), 1e-9);
    }
}
