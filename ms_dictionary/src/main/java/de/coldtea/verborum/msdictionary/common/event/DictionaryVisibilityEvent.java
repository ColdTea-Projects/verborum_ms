package de.coldtea.verborum.msdictionary.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

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
