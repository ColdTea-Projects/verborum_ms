package de.coldtea.verborum.msuser.common.utils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import de.coldtea.verborum.msuser.common.exception.InvalidUUIDException;

import java.util.UUID;

public class UUIDValidator implements ConstraintValidator<ValidUUID, String> {

    private String fieldName;

    @Override
    public void initialize(ValidUUID constraintAnnotation) {
        this.fieldName = constraintAnnotation.fieldName();
    }

    @Override
    public boolean isValid(String uuidString, ConstraintValidatorContext constraintValidatorContext) {
        // Null-handling belongs to @NotBlank — UUID.fromString(null) would throw an unhandled NPE
        if (uuidString == null) return true;

        try {
            UUID.fromString(uuidString);
            return true;
        } catch (IllegalArgumentException e) {
            throw new InvalidUUIDException("Invalid UUID for field: " + fieldName);
        }
    }
}
