package de.coldtea.verborum.msuser.common.exception;

import java.io.Serial;

public class RecordNotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 12312135154L;

    public RecordNotFoundException(String fieldName) {
        super(fieldName);
    }
}
