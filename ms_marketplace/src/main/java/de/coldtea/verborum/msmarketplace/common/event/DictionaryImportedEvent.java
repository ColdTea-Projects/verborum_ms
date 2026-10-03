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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryImportedEvent {

    private String dictionaryId;

    private String keycloakId;

    private OffsetDateTime eventTimestamp;
}
