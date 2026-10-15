package de.coldtea.verborum.msmarketplace.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Published on `dictionary.imported` when a user imports a listed dictionary (P4-07). ms_user adds it
 * to the importer's vault.
 * <p>
 * The contract was fixed by ms_user's consumer at P2-09, before this publisher existed — the field
 * names must stay exactly these. <b>`keycloakId` is the importer's JWT subject</b>, the only user id
 * this service knows; ms_user resolves it to its own `user_id`.
 * <p>
 * Sent on every successful import call, including a repeat: ms_user's vault insert is idempotent,
 * and re-sending repairs a vault entry whose first event was lost.
 * <p>
 * `publisherId` (P4N-06) is the listing owner's JWT subject, for ms_notification's silent push to the
 * publisher. It is additive: ms_user's consumer deserializes with an object mapper that ignores unknown
 * properties, so it neither needs the field nor breaks on it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryImportedEvent {

    private String dictionaryId;

    private String keycloakId;

    private String publisherId;

    /**
     * The importing install's `X-Device-Id` (P4N-14), null when the client sent none. ms_user carries it
     * onto `vault.changed` so the importer's *other* devices are nudged and this one is not.
     */
    private String deviceId;

    private OffsetDateTime eventTimestamp;
}
