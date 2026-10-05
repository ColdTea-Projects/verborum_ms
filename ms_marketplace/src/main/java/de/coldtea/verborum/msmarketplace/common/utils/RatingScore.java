package de.coldtea.verborum.msmarketplace.common.utils;

/**
 * The two numbers a listing's ratings are shown and ranked by (P4-19).
 * <p>
 * Ranking uses a Bayesian average, `(sum + C·m) / (count + C)`: every listing starts as if it had C
 * ratings of m stars, so one 5-star vote moves it only a little, and a listing reaches the top of
 * "top rated" by being rated well by many importers. Display uses the plain average.
 * <p>
 * m and C live here, and the 05-02 migration's DEFAULT for `rating_score` is m — changing either
 * means a migration that recomputes every row.
 */
public class RatingScore {

    /** m — the score a listing has before anyone rates it: the middle of the 1–5 scale. */
    public static final double PRIOR_MEAN = 3.0;

    /** C — how many imaginary m-star ratings every listing starts with. */
    public static final int PRIOR_WEIGHT = 5;

    /** The rating_score of an unrated listing, m. */
    public static final double UNRATED = PRIOR_MEAN;

    private RatingScore() {
    }

    /** The average shown on a listing, to one decimal; null when nobody has rated it yet. */
    public static Double average(Integer ratingSum, Integer ratingCount) {
        if (ratingSum == null || ratingCount == null || ratingCount == 0) {
            return null;
        }
        return Math.round(ratingSum * 10.0 / ratingCount) / 10.0;
    }
}
