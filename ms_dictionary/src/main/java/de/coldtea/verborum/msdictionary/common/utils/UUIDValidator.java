package de.coldtea.verborum.msdictionary.common.utils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import de.coldtea.verborum.msdictionary.common.exception.InvalidUUIDException;

import java.util.UUID;

public class UUIDValidator implements ConstraintValidator<ValidUUID, String> {

    private String fieldName;

    @Override
    public void initialize(ValidUUID constraintAnnotation) {
        this.fieldName = constraintAnnotation.fieldName();
    }

    @Override
    public boolean isValid(String uuidString, ConstraintValidatorContext constraintValidatorContext) {
        try {
            UUID.fromString(uuidString);
            return true;
        } catch (IllegalArgumentException e) {
            throw new InvalidUUIDException("Invalid UUID for field: " + fieldName);
        }
    }
}
