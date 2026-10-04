package de.coldtea.verborum.msmarketplace.common.utils;

import java.util.Locale;

/**
 * Turns user input into a safe SQL LIKE pattern (P4-13, the publisher-name filter).
 */
public class LikePatternUtils {

    // Passed to the query as the LIKE ... ESCAPE character, so the two must be the same constant
    public static final char ESCAPE = '\\';

    private LikePatternUtils() {
    }

    /**
     * A case-insensitive "contains" pattern: "nna" → `%nna%`, which finds "Anna Bauer" when matched
     * against `lower(column)`. Trimmed and lowercased with `Locale.ROOT` (TR is supported, and
     * Turkish lowercasing differs). The input's `%`, `_` and the escape character itself are escaped,
     * or "a_b" would match "axb" and a lone "%" would match everything.
     *
     * @return the pattern, or null for null or blank input — no filter
     */
    public static String toContainsPattern(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String escape = String.valueOf(ESCAPE);
        String escaped = input.trim().toLowerCase(Locale.ROOT)
                .replace(escape, escape + escape)
                .replace("%", escape + "%")
                .replace("_", escape + "_");
        return "%" + escaped + "%";
    }
}
