package de.coldtea.verborum.msmarketplace.common.utils;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguagePairUtilsTest {

    @Test
    void toLangPair_BothDirections_GiveTheSamePair() {
        // Act + Assert
        assertEquals("DE-TR", LanguagePairUtils.toLangPair("DE", "TR"));
        assertEquals("DE-TR", LanguagePairUtils.toLangPair("TR", "DE"));
    }

    @Test
    void toLangPair_LowercaseCodes_AreUppercased() {
        // Act + Assert
        assertEquals("EN-TR", LanguagePairUtils.toLangPair("tr", "en"));
    }

    @Test
    void toLangPair_RequestedPair_IsMadeCanonical() {
        // Act + Assert
        assertEquals("DE-FR", LanguagePairUtils.toLangPair("FR-DE"));
        assertEquals("DE-FR", LanguagePairUtils.toLangPair("de-fr"));
    }

    @Test
    void toLangPair_TurkishDefaultLocale_UppercasedLocaleIndependently() {
        // Arrange — under a Turkish default locale "it" would become "İT" and match nothing
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));

        try {
            // Act + Assert
            assertEquals("IT-TR", LanguagePairUtils.toLangPair("tr-it"));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
