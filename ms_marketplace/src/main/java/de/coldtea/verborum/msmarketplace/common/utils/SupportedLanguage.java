package de.coldtea.verborum.msmarketplace.common.utils;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.INVALID_LANGUAGE_CODE;

/**
 * A language code from `supported.languages`, in any case.
 * <p>
 * <b>{@code @Constraint} is what makes this run.</b> Without it Bean Validation treats the annotation
 * as inert metadata and never calls the validator — the state ms_dictionary's and ms_user's copies are
 * in (found at P4-06: they accept `XX` and non-UUID ids). On a controller parameter it is enforced by
 * Spring MVC's built-in method validation, which raises `HandlerMethodValidationException` (→ 400).
 */
@Constraint(validatedBy = SupportedLanguageValidator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface SupportedLanguage {

    String message() default INVALID_LANGUAGE_CODE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
