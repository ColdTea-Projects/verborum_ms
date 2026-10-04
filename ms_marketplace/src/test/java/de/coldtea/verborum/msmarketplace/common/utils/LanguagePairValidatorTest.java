package de.coldtea.verborum.msmarketplace.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguagePairValidatorTest {

    // A stray space after a comma, as in a hand-edited property, must not make a code unmatchable
    private final LanguagePairValidator validator = new LanguagePairValidator("EN,DE, TR,FR");

    @Test
    void isValid_SupportedPairInEitherCaseAndDirection_IsValid() {
        // Act + Assert
        assertTrue(validator.isValid("EN-TR", null));
        assertTrue(validator.isValid("tr-en", null));
        assertTrue(validator.isValid("De-Fr", null));
    }

    @Test
    void isValid_Null_IsLeftToRequiredChecks() {
        // Act + Assert
        assertTrue(validator.isValid(null, null));
    }

    @Test
    void isValid_UnsupportedCode_IsInvalid() {
        // Act + Assert
        assertFalse(validator.isValid("EN-XX", null));
        assertFalse(validator.isValid("LT-EN", null));
    }

    @Test
    void isValid_SameCodeTwice_IsInvalid() {
        // Act + Assert
        assertFalse(validator.isValid("EN-EN", null));
        assertFalse(validator.isValid("en-EN", null));
    }

    @Test
    void isValid_Malformed_IsInvalid() {
        // Act + Assert
        assertFalse(validator.isValid("", null));
        assertFalse(validator.isValid("EN", null));
        assertFalse(validator.isValid("ENTR", null));
        assertFalse(validator.isValid("EN-", null));
        assertFalse(validator.isValid("-TR", null));
        assertFalse(validator.isValid("EN-TR-DE", null));
        assertFalse(validator.isValid("EN_TR", null));
        assertFalse(validator.isValid(" EN-TR", null));
    }
}
