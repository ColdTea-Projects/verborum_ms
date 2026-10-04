package de.coldtea.verborum.msmarketplace.common.utils;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LikePatternUtilsTest {

    @Test
    void toContainsPattern_PartOfAName_MatchesAnywhereInLowercase() {
        // Act + Assert — matched against lower(display_name), so "NNA" must find "Anna Bauer"
        assertEquals("%nna%", LikePatternUtils.toContainsPattern("NNA"));
        assertEquals("%anna b%", LikePatternUtils.toContainsPattern("  Anna B "));
    }

    @Test
    void toContainsPattern_Wildcards_AreEscaped() {
        // Act + Assert — "%" alone must not match every name, "a_b" must not match "axb"
        assertEquals("%\\%%", LikePatternUtils.toContainsPattern("%"));
        assertEquals("%a\\_b%", LikePatternUtils.toContainsPattern("a_b"));
    }

    @Test
    void toContainsPattern_EscapeCharacter_IsEscapedItself() {
        // Act + Assert
        assertEquals("%a\\\\b%", LikePatternUtils.toContainsPattern("a\\b"));
    }

    @Test
    void toContainsPattern_NullOrBlank_IsNoFilter() {
        // Act + Assert
        assertNull(LikePatternUtils.toContainsPattern(null));
        assertNull(LikePatternUtils.toContainsPattern("   "));
    }

    @Test
    void toContainsPattern_TurkishDefaultLocale_LowercasedLocaleIndependently() {
        // Arrange — under a Turkish default locale "I" would become dotless "ı" and miss "ivan"
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));

        try {
            // Act + Assert
            assertEquals("%ivan%", LikePatternUtils.toContainsPattern("IVAN"));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
