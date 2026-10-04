package de.coldtea.verborum.msmarketplace.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

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

    /**
     * The dictionary's tags (P4-12): normalised (trimmed, lowercase), sorted; an untagged dictionary
     * sends an empty list. Full state, not a delta (rule 2): the consumer replaces what it holds.
     * <p>
     * Null means the publisher predates P4-12 (or the message is an old one replayed from the DLQ):
     * tags unknown, so the held tags are kept rather than wiped.
     */
    private List<String> tags;

    private OffsetDateTime updatedAt;

    private OffsetDateTime eventTimestamp;
}
