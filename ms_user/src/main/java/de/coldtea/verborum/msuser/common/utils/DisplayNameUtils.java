package de.coldtea.verborum.msuser.common.utils;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Display names are deliberately not unique — two people may both be "Anna Bauer". What is refused is
 * a name that reads as the platform speaking (SEC-10, security audit 2026-10-05): Forum listings show
 * the publisher's name, so "Verborum Team" or "Admin" would lend someone else's dictionary an
 * authority it does not have.
 * <p>
 * Matching is done on a folded form so the obvious disguises do not get through: case, accents
 * ("Vérborum"), look-alike digits ("4dm1n") and separators ("V e r b o r u m", "admin_42").
 */
public class DisplayNameUtils {

    /**
     * Refused anywhere in the name, separators ignored. Kept to terms long and specific enough that an
     * ordinary name rarely contains them — "admin" does catch "Badminton", accepted as the price of
     * catching "xAdminx".
     */
    private static final Set<String> RESERVED_ANYWHERE = Set.of("verborum", "coldtea", "admin", "moderator", "official");

    /**
     * Refused only as a whole word: short or common enough that matching inside other words would
     * block real names ("Steam", "Modern", "Rooted", "Helpful", "Robot").
     */
    private static final Set<String> RESERVED_WORDS = Set.of(
            "support", "staff", "team", "system", "mod", "root", "security", "help", "helpdesk", "bot");

    // Digits and symbols commonly swapped in for letters. Applied before splitting into words, so
    // "t3am" and "$upport" fold to their reserved forms
    private static final Map<Character, Character> LOOK_ALIKES = Map.of(
            '0', 'o', '1', 'i', '3', 'e', '4', 'a', '5', 's', '7', 't', '@', 'a', '$', 's', '!', 'i');

    private DisplayNameUtils() {
    }

    /** True when the name contains a reserved term (see the class comment for how it is matched). */
    public static boolean isReserved(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return false;
        }
        String folded = fold(displayName);

        String joined = folded.replaceAll("[^a-z]", "");
        if (RESERVED_ANYWHERE.stream().anyMatch(joined::contains)) {
            return true;
        }

        return Arrays.stream(folded.split("[^a-z]+")).anyMatch(RESERVED_WORDS::contains);
    }

    /**
     * Lowercase with Locale.ROOT (a Turkish default locale would turn "I" into a dotless "ı" and miss
     * "ADMIN"), accents stripped via NFKD, look-alikes replaced.
     */
    private static String fold(String displayName) {
        String decomposed = Normalizer.normalize(displayName, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        StringBuilder folded = new StringBuilder(decomposed.length());
        for (char c : decomposed.toCharArray()) {
            folded.append(LOOK_ALIKES.getOrDefault(c, c));
        }
        return folded.toString();
    }
}
