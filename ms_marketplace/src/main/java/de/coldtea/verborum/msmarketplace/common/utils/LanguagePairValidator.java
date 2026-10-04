package de.coldtea.verborum.msmarketplace.common.utils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static de.coldtea.verborum.msmarketplace.common.utils.LanguagePairUtils.SEPARATOR;

/**
 * Returns false rather than throwing, for the same reason as {@link SupportedLanguageValidator}: an
 * exception inside `isValid` surfaces as a 500, a false is a normal 400.
 */
public class LanguagePairValidator implements ConstraintValidator<LanguagePair, String> {

    private final List<String> supportedLanguages;

    public LanguagePairValidator(@Value("${supported.languages}") String supportedLanguages) {
        this.supportedLanguages = Arrays.stream(supportedLanguages.split(","))
                .map(String::trim)
                .toList();
    }

    @Override
    public boolean isValid(String pair, ConstraintValidatorContext constraintValidatorContext) {
        // Null-handling belongs to required=true / @NotNull
        if (pair == null) return true;

        // limit -1 keeps trailing empty parts, so "EN-" and "EN-TR-" are two and three parts, not one
        String[] languages = pair.split(SEPARATOR, -1);
        if (languages.length != 2) return false;

        String first = languages[0].toUpperCase(Locale.ROOT);
        String second = languages[1].toUpperCase(Locale.ROOT);

        // EN-EN is not a dictionary anyone publishes; refuse it rather than silently match nothing
        return !first.equals(second) && supportedLanguages.contains(first) && supportedLanguages.contains(second);
    }
}
