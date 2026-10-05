package de.coldtea.verborum.msmarketplace.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RatingScoreTest {

    @Test
    void average_NoRatings_IsNull() {
        assertNull(RatingScore.average(0, 0));
        assertNull(RatingScore.average(null, null));
    }

    @Test
    void average_RoundsToOneDecimal() {
        assertEquals(4.3, RatingScore.average(13, 3));   // 4.333…
        assertEquals(4.7, RatingScore.average(14, 3));   // 4.666…
        assertEquals(5.0, RatingScore.average(5, 1));
    }

    @Test
    void unratedScore_IsThePriorMean() {
        // The 05-02 migration's DEFAULT for rating_score must equal this
        assertEquals(3.0, RatingScore.UNRATED);
    }
}
