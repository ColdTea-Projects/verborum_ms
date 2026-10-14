package de.coldtea.verborum.msdictionary.common.utils;

import de.coldtea.verborum.msdictionary.common.event.DictionaryChangedEvent;
import de.coldtea.verborum.msdictionary.common.event.OutboundEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;

import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_CHANGED;

/**
 * Raises `dictionary.changed` (P4N-06) for the services that change a user's data. Callers decide
 * <b>whether</b> something changed — this class only builds and queues the event, which
 * OutboundEventPublisher sends after commit (rule 1).
 * <p>
 * The writing device comes from the optional `X-Device-Id` request header, read here from the current
 * request rather than threaded through every service method. That is deliberate and narrow: it is event
 * decoration, not ownership — who owns the data always arrives as an explicit argument. Outside a
 * request (a RabbitMQ listener) there is no header and the id is simply null. A value that is not a
 * canonical UUID is dropped rather than forwarded.
 */
@Component
@RequiredArgsConstructor
public class DictionaryChangeNotifier {

    public static final String DEVICE_ID_HEADER = "X-Device-Id";

    private final ApplicationEventPublisher eventPublisher;

    private final UUIDValidator uuidValidator = new UUIDValidator();

    public void dictionaryChanged(String keycloakId, String dictionaryId) {
        eventPublisher.publishEvent(new OutboundEvent(
                ROUTING_KEY_DICTIONARY_CHANGED,
                DictionaryChangedEvent.builder()
                        .keycloakId(keycloakId)
                        .dictionaryId(dictionaryId)
                        .deviceId(currentDeviceId())
                        .eventTimestamp(OffsetDateTime.now())
                        .build()));
    }

    private String currentDeviceId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        String deviceId = servletAttributes.getRequest().getHeader(DEVICE_ID_HEADER);
        // UUIDValidator treats null as valid (that is @NotBlank's job), so null is handled first
        return deviceId != null && uuidValidator.isValid(deviceId, null) ? deviceId : null;
    }
}
