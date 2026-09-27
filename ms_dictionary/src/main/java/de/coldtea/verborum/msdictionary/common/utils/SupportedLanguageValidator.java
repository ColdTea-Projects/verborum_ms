package de.coldtea.verborum.msdictionary.common.utils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Returns false rather than throwing (P4-09): an exception thrown inside `isValid` is wrapped by the
 * validation framework in a `ValidationException` no handler maps — a 500 instead of a 400.
 * <p>
 * Not a @Component: Spring's constraint-validator factory instantiates it and injects the property.
 */
public class SupportedLanguageValidator implements ConstraintValidator<SupportedLanguage, String> {

    private final List<String> supportedLanguages;

    public SupportedLanguageValidator(@Value("${supported.languages}") String supportedLanguages) {
        // trim: a stray space after a comma in the property would otherwise make that code unmatchable
        this.supportedLanguages = Arrays.stream(supportedLanguages.split(","))
                .map(String::trim)
                .toList();
    }

    @Override
    public boolean isValid(String language, ConstraintValidatorContext constraintValidatorContext) {
        // Null-handling belongs to @NotBlank
        if (language == null) return true;

        // Locale.ROOT: default-locale uppercasing turns Turkish "i" into "İ", and TR is supported here
        return supportedLanguages.contains(language.toUpperCase(Locale.ROOT));
    }
}
