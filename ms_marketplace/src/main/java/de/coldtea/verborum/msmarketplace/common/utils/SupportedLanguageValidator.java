package de.coldtea.verborum.msmarketplace.common.utils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Returns false rather than throwing, unlike ms_dictionary's copy. An exception thrown inside
 * `isValid` is wrapped by the validation framework in a `ValidationException`, which no specific
 * handler catches — it would surface as a 500. Returning false produces a normal validation failure
 * and a 400.
 * <p>
 * Instantiated by Spring's constraint-validator factory, which is what injects the property.
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
        // Null-handling belongs to required=true / @NotBlank
        if (language == null) return true;

        // Locale.ROOT: default-locale uppercasing turns Turkish "i" into "İ", and TR is supported here
        return supportedLanguages.contains(language.toUpperCase(Locale.ROOT));
    }
}
