package de.coldtea.verborum.msmarketplace.common.utils;

import java.util.Locale;

/**
 * The canonical, direction-free form of a language pair (P4-11): both codes uppercase, in
 * alphabetical order, joined by {@value #SEPARATOR}. EN→TR and TR→EN are both `EN-TR`.
 * <p>
 * Browse ignores direction — a learner of German with Turkish wants DE→TR and TR→DE dictionaries
 * alike — so every listing stores this form in `lang_pair`, and a requested pair is converted the same
 * way. The filter is then one indexed `lang_pair IN (...)` instead of an OR over both directions.
 * <p>
 * Ordering is String's code-point order. The backfill migration orders with `COLLATE "C"` for the same
 * result; a locale-aware collation could order differently and leave stored pairs unmatched.
 */
public class LanguagePairUtils {

    public static final String SEPARATOR = "-";

    private LanguagePairUtils() {
    }

    /** The canonical pair of a listing's two codes, in either order and either case. */
    public static String toLangPair(String firstLanguage, String secondLanguage) {
        // Locale.ROOT: TR is supported, and Turkish uppercasing turns "i" into "İ"
        String first = firstLanguage.toUpperCase(Locale.ROOT);
        String second = secondLanguage.toUpperCase(Locale.ROOT);
        return first.compareTo(second) <= 0 ? first + SEPARATOR + second : second + SEPARATOR + first;
    }

    /**
     * The canonical form of a requested pair such as `tr-de`. Expects a value that passed
     * {@link LanguagePair} validation — two codes around one separator.
     */
    public static String toLangPair(String pair) {
        String[] languages = pair.split(SEPARATOR, -1);
        return toLangPair(languages[0], languages[1]);
    }
}
