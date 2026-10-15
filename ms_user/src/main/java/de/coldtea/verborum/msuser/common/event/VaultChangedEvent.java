package de.coldtea.verborum.msuser.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Published on `vault.changed` (P4N-14) when a user's vault really changed: a dictionary was added by
 * an import, or removed by an unsubscribe. Consumed by ms_notification, which sends the user's other
 * devices a silent `SYNC` so their library and Forum screens update without waiting for a refresh.
 * <p>
 * Published **here** rather than from ms_marketplace because the vault entry is created by this service,
 * asynchronously, a moment after the import returns: an event sent by the marketplace could reach the
 * other device before the entry exists. Fires on change only — a redelivered import and a removal of
 * something that was not there publish nothing.
 * <p>
 * `keycloakId` is the owner's JWT subject, the id every other service stores. `deviceId` is the writing
 * install's `X-Device-Id` (null when the client sent none) and is skipped by the consumer. Ids only.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaultChangedEvent {

    private String keycloakId;

    private String dictionaryId;

    private String deviceId;

    private OffsetDateTime eventTimestamp;
}
