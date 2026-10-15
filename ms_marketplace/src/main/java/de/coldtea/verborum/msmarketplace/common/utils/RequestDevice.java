package de.coldtea.verborum.msmarketplace.common.utils;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The install that is making the current request, from the optional `X-Device-Id` header (P4N-14).
 * <p>
 * Event decoration, not ownership — who owns the data always arrives as an explicit argument, from the
 * token. It lets ms_notification skip the writing device when it nudges the account's other ones. Read
 * from the current request rather than threaded through every method; outside a request, or when the
 * value is not a canonical UUID, it is null. Never forwarded as free text.
 */
@Component
public class RequestDevice {

    public static final String DEVICE_ID_HEADER = "X-Device-Id";

    private final UUIDValidator uuidValidator = new UUIDValidator();

    public String currentDeviceId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        String deviceId = servletAttributes.getRequest().getHeader(DEVICE_ID_HEADER);
        // UUIDValidator treats null as valid (that is @NotBlank's job), so null is handled first
        return deviceId != null && uuidValidator.isValid(deviceId, null) ? deviceId : null;
    }
}
