package de.coldtea.verborum.msnotification.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Consumed from `dictionary.imported` (ms_marketplace, P4-07; `publisherId` added in P4N-06).
 * `keycloakId` is the <b>importer</b> — the actor; `publisherId` is the listing's owner — the recipient.
 * Both are JWT subjects.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryImportedEvent {

    private String dictionaryId;

    private String keycloakId;

    private String publisherId;

    private OffsetDateTime eventTimestamp;
}
