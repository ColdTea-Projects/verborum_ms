package de.coldtea.verborum.msdictionary.common.utils;

import de.coldtea.verborum.msdictionary.common.constants.LanguageConstants;
import de.coldtea.verborum.msdictionary.common.exception.InvalidLanguageCodeException;
import de.coldtea.verborum.msdictionary.common.exception.InvalidUUIDException;

import java.util.List;
import java.util.UUID;

public class LanguageCodeValidator {
    public static boolean isValidLanguageCode(String languageCode) {
            if(LanguageConstants.supportedLanguages.contains(languageCode)) return true;
            else throw new InvalidLanguageCodeException();
    }
}