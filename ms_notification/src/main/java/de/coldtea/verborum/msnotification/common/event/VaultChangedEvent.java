package de.coldtea.verborum.msnotification.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Consumed from `vault.changed` (ms_user, P4N-14): a dictionary was added to or removed from a user's
 * vault — a Forum download or an unsubscribe. Consumer-side copy; only the JSON field names have to agree.
 * <p>
 * `keycloakId` is the owner's JWT subject and the recipient of the sync nudge. `deviceId` is the install
 * that made the change, null when the client sent none; that install is skipped.
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
