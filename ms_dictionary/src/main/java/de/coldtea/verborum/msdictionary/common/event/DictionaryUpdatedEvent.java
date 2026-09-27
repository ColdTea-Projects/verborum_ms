package de.coldtea.verborum.msdictionary.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Published on `dictionary.updated` when a dictionary that is — and stays — public changes one of
 * the fields ms_marketplace lists: `name`, `fromLang` or `toLang` (roadmap P4-03). Visibility flips
 * have their own events; this closes the gap where a rename of a public dictionary emitted nothing
 * and the listing went stale.
 * <p>
 * Carries the full listing payload (rule 2) and the dictionary's own `updatedAt`, which the consumer
 * compares against what it holds to drop out-of-order deliveries (rule 4).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryUpdatedEvent {

    private String dictionaryId;

    private String userId;

    private String fromLang;

    private String toLang;

    private String dictionaryName;

    private OffsetDateTime updatedAt;

    private OffsetDateTime eventTimestamp;
}
