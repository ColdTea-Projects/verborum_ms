package de.coldtea.verborum.msdictionary.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Published on `dictionary.changed` (P4N-06) when something in a user's dictionary really changed — the
 * dictionary itself, any of its words, or its tags. Consumed by ms_notification, which nudges the user's
 * <b>other</b> devices with a silent push so they sync without waiting for a manual refresh.
 * <p>
 * Distinct from `dictionary.updated`, which covers only a public dictionary's listing fields and so
 * cannot serve sync. Fires on change only (rule 6): a re-upload of identical content publishes nothing,
 * which is what stops two devices from pinging each other forever.
 * <p>
 * `keycloakId` is the owner's JWT subject. `deviceId` is the writing install's id from the optional
 * `X-Device-Id` request header — null when the client sent none or the change was made by the server
 * itself (joining or leaving the marketplace) — and lets the consumer skip the device that caused it.
 * It carries no content: the apps fetch the data themselves.
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
