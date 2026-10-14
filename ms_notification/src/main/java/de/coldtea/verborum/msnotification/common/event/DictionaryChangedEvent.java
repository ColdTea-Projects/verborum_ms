package de.coldtea.verborum.msnotification.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Consumed from `dictionary.changed` (ms_dictionary, P4N-06): a real change to a user's dictionary, its
 * words or its tags. Consumer-side copy — only the JSON field names have to agree with the publisher.
 * <p>
 * `keycloakId` is the owner's JWT subject. `deviceId` is the writing install's `X-Device-Id`, null when
 * the client sent none or the server made the change itself; the device it names is skipped.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryChangedEvent {

    private String keycloakId;

    private String dictionaryId;

    private String deviceId;

    private OffsetDateTime eventTimestamp;
}
