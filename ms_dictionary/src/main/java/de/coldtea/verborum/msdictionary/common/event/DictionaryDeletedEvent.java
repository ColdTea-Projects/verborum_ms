package de.coldtea.verborum.msdictionary.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Published on `dictionary.deleted` when a dictionary is removed. Consumed by ms_marketplace to
 * drop the corresponding listing.
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

    private LocalDateTime eventTimestamp;
}
