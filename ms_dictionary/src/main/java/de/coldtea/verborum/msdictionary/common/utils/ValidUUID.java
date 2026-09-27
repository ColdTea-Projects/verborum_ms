package de.coldtea.verborum.msdictionary.common.utils;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static de.coldtea.verborum.msdictionary.common.constants.DTOMessageConstants.INVALID_UUID;

/**
 * A UUID string in canonical 8-4-4-4-12 hex form; null passes (that is @NotBlank's job).
 * <p>
 * <b>{@code @Constraint} is what makes this run</b> (roadmap P4-09). Until 2026-09-27 it was missing,
 * so Bean Validation treated the annotation as inert metadata and non-UUID ids were stored. On a DTO
 * field a failure is a `MethodArgumentNotValidException`, on a controller parameter a
 * `HandlerMethodValidationException` — both 400 via GlobalExceptionHandler. The error names the field,
 * so the annotation needs no field-name attribute.
 */
@Constraint(validatedBy = UUIDValidator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface ValidUUID {

    String message() default INVALID_UUID;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
