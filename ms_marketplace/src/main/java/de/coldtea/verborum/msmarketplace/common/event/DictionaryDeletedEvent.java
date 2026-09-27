package de.coldtea.verborum.msmarketplace.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Published by ms_dictionary on `dictionary.deleted` when a dictionary is removed. This service hides
 * the listing (P4-05) and the next snapshot removes the row. `eventTimestamp` doubles as the
 * deletion's ordering key, since there is no `updatedAt` for a dictionary that no longer exists.
 * <p>
 * Deliberately minimal, mirroring `UserDeletedEvent`: a consumer only needs to identify what to
 * remove, and the dictionary is gone by the time the event is read, so there is nothing to call
 * back for. `userId` is carried so a consumer can scope the removal without a lookup.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryDeletedEvent {

    private String dictionaryId;

    private String userId;

    private OffsetDateTime eventTimestamp;
}
