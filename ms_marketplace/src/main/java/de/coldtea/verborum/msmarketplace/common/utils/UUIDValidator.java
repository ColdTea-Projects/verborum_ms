package de.coldtea.verborum.msmarketplace.common.utils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Returns false rather than throwing (P4-09). An exception thrown inside `isValid` is wrapped by the
 * validation framework in a `ValidationException` that no handler maps, which surfaces as a 500.
 * <p>
 * A regex, not `UUID.fromString`: the JDK parser is lenient and accepts non-canonical strings such as
 * `1-1-1-1-1`, which would then be stored as ids the clients never generate.
 */
public class UUIDValidator implements ConstraintValidator<ValidUUID, String> {

    private static final Pattern CANONICAL_UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Override
    public boolean isValid(String uuidString, ConstraintValidatorContext constraintValidatorContext) {
        // Null-handling belongs to @NotBlank
        if (uuidString == null) return true;

        return CANONICAL_UUID.matcher(uuidString).matches();
    }
}
