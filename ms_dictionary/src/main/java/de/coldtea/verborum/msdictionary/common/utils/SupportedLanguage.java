package de.coldtea.verborum.msdictionary.common.utils;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static de.coldtea.verborum.msdictionary.common.constants.DTOMessageConstants.INVALID_LANGUAGE_CODE;

/**
 * A language code from `supported.languages`, in any case.
 * <p>
 * <b>{@code @Constraint} is what makes this run</b> (roadmap P4-09). Until 2026-09-27 it was missing,
 * so Bean Validation treated the annotation as inert metadata: `fromLang: "XX"` was stored and
 * `/words/language/from/XX` answered 200. Same shape as ms_marketplace's copy (P4-06).
 */
@Constraint(validatedBy = SupportedLanguageValidator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface SupportedLanguage {

    String message() default INVALID_LANGUAGE_CODE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
