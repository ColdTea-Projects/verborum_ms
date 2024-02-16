package de.coldtea.verborum.msdictionary.common.utils;

import de.coldtea.verborum.msdictionary.common.exception.InvalidLanguageCodeException;
import de.coldtea.verborum.msdictionary.common.utils.SupportedLanguage;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

import static de.coldtea.verborum.msdictionary.common.constants.DTOMessageConstants.INVALID_LANGUAGE_CODE;

@Component
public class SupportedLanguageValidator implements ConstraintValidator<SupportedLanguage, String> {

    private final List<String> supportedLanguages;

    public SupportedLanguageValidator(@Value("${supported.languages}") String supportedLanguages) {
        this.supportedLanguages = Arrays.asList(supportedLanguages.split(","));
    }

    @Override
    public boolean isValid(String language, ConstraintValidatorContext constraintValidatorContext) {
        if (!supportedLanguages.contains(language.toUpperCase())) {
            throw new InvalidLanguageCodeException(INVALID_LANGUAGE_CODE + language);
        }
        return true;
    }
}
