package de.coldtea.verborum.msmarketplace.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Published on `dictionary.visibility.public` / `dictionary.visibility.private` when a
 * dictionary's `is_public` flag changes. Consumed by ms_marketplace to create or remove the
 * marketplace listing — it carries the full listing payload so the consumer does not have to
 * call back into ms_dictionary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryVisibilityEvent {

    private String dictionaryId;

    private String userId;

    private Boolean isPublic;

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

    /**
     * The dictionary's own `updatedAt` — the ordering key for the marketplace projection (rule 4 in
     * docs/agent/rabbitmq.md). A consumer must ignore an event whose `updatedAt` is not newer than
     * the state it already holds: messages can arrive out of order, and two quick edits delivered in
     * reverse would otherwise leave the listing permanently showing the older values, with nothing
     * to signal it.
     * <p>
     * Distinct from `eventTimestamp`, which is when the event was raised, not when the data changed.
     */
    private OffsetDateTime updatedAt;

    private OffsetDateTime eventTimestamp;
}
