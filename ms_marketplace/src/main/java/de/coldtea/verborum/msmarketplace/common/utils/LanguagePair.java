package de.coldtea.verborum.msmarketplace.common.utils;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.INVALID_LANGUAGE_PAIR;

/**
 * A language pair for the browse filter (P4-11): two different codes from `supported.languages`
 * joined by a hyphen, in any case and either order — `EN-TR`, `tr-en`.
 * <p>
 * TYPE_USE so it can sit on the elements of a list parameter: `List<@LanguagePair String> pair`.
 * Like {@link SupportedLanguage}, `@Constraint` is what makes it run at all.
 */
@Constraint(validatedBy = LanguagePairValidator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
public @interface LanguagePair {

    String message() default INVALID_LANGUAGE_PAIR;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
